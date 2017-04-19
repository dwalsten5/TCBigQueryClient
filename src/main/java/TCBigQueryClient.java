package main.java;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import java.io.File;
import java.io.FileWriter;

import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.BigQueryOptions;
import com.google.cloud.bigquery.Field;
import com.google.cloud.bigquery.FieldValue;
import com.google.cloud.bigquery.Job;
import com.google.cloud.bigquery.JobId;
import com.google.cloud.bigquery.JobInfo;
import com.google.cloud.bigquery.QueryJobConfiguration;
import com.google.cloud.bigquery.QueryResponse;
import com.google.cloud.bigquery.QueryResult;
import com.opencsv.CSVWriter;

import main.model.TCBigQueryContract;
import main.model.User;

public class TCBigQueryClient {
	
	public final static String EXPORT_DIRECTORY = "/Users/doranwalsten/Documents/CBID/TechConnect/Usage/";
	
	
	public static void main(String[] args) throws IOException, FileNotFoundException, InterruptedException, TimeoutException {
		BigQuery bigquery = BigQueryOptions.getDefaultInstance().getService();
	    // [END create_client]
		
		//Get list of unique users
		List<User> users = getUserList(bigquery,"2017-03-01","2017-04-07");
		for (User u : users) {
			System.out.println(u.getId());
		}
	    
		//Write all session events to CSV file
	    writeAllSessionEvents(bigquery,"2017-03-01","2017-04-07");
	}
	
	
	/**
	 * Returns the list of unique users between the dates given
	 * @param bq - BigQuery instance in order to actually run the query
	 * @param startDate - in form "yyyy-MM-dd"
	 * @param endDate - in form "yyyy-MM-dd"
	 * @return
	 */
	private static List<User> getUserList(BigQuery bq, String startDate, String endDate) throws TimeoutException, InterruptedException {
		//Get the appropriate query based on the dates
		
		String query = String.format(TCBigQueryContract.UserDataEntry.LIST_UNIQUE_USER, startDate,endDate);
		QueryResult result;
		
		try {
			result = completeQuery(bq,query);
			
			//Initialize the list of users
		    ArrayList<User> users = new ArrayList<User>();
		    
		    while (result != null) {
		    	  Iterator<List<FieldValue>> iter = result.iterateAll();
		    	  while (iter.hasNext()) {
		    	    List<FieldValue> row = iter.next();
		    	    User user = new User(row.get(0).getStringValue(),row.get(1).getStringValue(),row.get(2).getStringValue());
		    	    users.add(user);
		    	  }
		    	  result = result.getNextPage();
		    }
		    
		    return users;
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}
	
	/**
	 * Gets all session-related counts during the time interval desired and writes to a csv file
	 * @param bq - BigQuery instance in order to run query
	 * @param startDate - in form "yyyy-MM-dd"
	 * @param endDate - in form "yyyy-MM-dd"
	 */
	private static void writeAllSessionEvents(BigQuery bq,String startDate, String endDate) {
		String query = String.format(TCBigQueryContract.SessionDataEntry.ALL_EVENTS_DAILY_COUNT, startDate,endDate,startDate,endDate);
		QueryResult result;
		
		try {
			result = completeQuery(bq,query);
			
			//Prepare CSVWriter in order to write output
		    File file = new File(EXPORT_DIRECTORY, "TotalCount.csv");
		    CSVWriter writer = new CSVWriter(new FileWriter(file));
		    List<Field> fields = result.getSchema().getFields();
		    
		    //Get the column names of the result
		    ArrayList<String> dataRow = new ArrayList<String>();
		    for (Field f : fields) {
		    	dataRow.add(f.getName());
		    }
		    String[] dataRowArray = new String[dataRow.size()];
		    dataRowArray = dataRow.toArray(dataRowArray);
		    writer.writeNext(dataRowArray);
		    dataRow.clear();
		    
		    while (result != null) {
		    	  Iterator<List<FieldValue>> iter = result.iterateAll();
		    	  while (iter.hasNext()) {
		    	    List<FieldValue> row = iter.next();
		    	    
		    	    for (FieldValue f : row) {
		    	    	dataRow.add(f.getStringValue());
		    	    }
		    	    dataRowArray = new String[dataRow.size()];
				    dataRowArray = dataRow.toArray(dataRowArray);
				    writer.writeNext(dataRowArray);
				    dataRow.clear();
		    	  }
		    	  result = result.getNextPage();
		    }
		    
		    writer.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	
	/**
	 * Wrapper for all of the code needed to actually execute a query with BigQuery
	 * @param bq
	 * @param query
	 * @return
	 * @throws TimeoutException
	 * @throws InterruptedException
	 */
	private static QueryResult completeQuery(BigQuery bq, String query) throws TimeoutException, InterruptedException  {
		QueryJobConfiguration queryConfig =
		        QueryJobConfiguration.newBuilder(query)
		            .setUseLegacySql(true) //This is whatever I've been using online, so I'm going to stick with it
		            .build();

		    // Create a job ID so that we can safely retry.
		    JobId jobId = JobId.of(UUID.randomUUID().toString());
		    Job queryJob = bq.create(JobInfo.newBuilder(queryConfig).setJobId(jobId).build());

		    // Wait for the query to complete.
		    queryJob = queryJob.waitFor();

		    // Check for errors
		    if (queryJob == null) {
		      throw new RuntimeException("Job no longer exists");
		    } else if (queryJob.getStatus().getError() != null) {
		      // You can also look at queryJob.getStatus().getExecutionErrors() for all
		      // errors, not just the latest one.
		      throw new RuntimeException(queryJob.getStatus().getError().toString());
		    }

		    // Get the results.
		    QueryResponse response = bq.getQueryResults(jobId);
		    return response.getResult();
	}
}
