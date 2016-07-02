package spade.utility;

public class LogManager extends java.util.logging.LogManager{

    /* Super class uses reset() in its callback when the system is
     * shutdown. To avoid it being reset before we are done logging, we
     * redefine it to do nothing. We then call shutdownReset() at the end
     * of our shutdown processing, to call the super class's reset().
     */
    
    static LogManager logManager;
    
    public LogManager(){
        logManager = this;
    }
    
    public void reset(){
    }
    
	private void superReset(){
            super.reset();
    }
    
    public static void shutdownReset(){
        logManager.superReset();
    }
} 
