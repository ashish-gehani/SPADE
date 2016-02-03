package spade.utility;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CommandUtility {
	
	private static Logger logger = Logger.getLogger(CommandUtility.class.getName());

	public static List<String> getOutputOfCommand(String ...commands) throws Exception{
		
		Process process = Runtime.getRuntime().exec(commands);
		
		final BufferedReader stdoutReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
		final BufferedReader stderrReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
		final List<String> lines = new ArrayList<String>();
		
		Thread stdoutThread = new Thread(new Runnable(){
			public void run(){
				try{
					String line = null;
					while((line = stdoutReader.readLine()) != null){
						lines.add(line);
					}
				}catch(Exception e){
					logger.log(Level.SEVERE, null, e);
				}
			}
		});
		
		Thread stderrThread = new Thread(new Runnable(){
			public void run(){
				try{
					String line = null;
					while((line = stderrReader.readLine()) != null){
						lines.add(line);
					}
				}catch(Exception e){
					logger.log(Level.SEVERE, null, e);
				}
			}
		});
		
		stdoutThread.join();
		stderrThread.join();
		
		stderrReader.close();
		stdoutReader.close();
		
		return lines;
	}
	
}
