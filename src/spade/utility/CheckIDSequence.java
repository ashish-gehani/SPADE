import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/*
 * Usage: java -cp . CheckIDSequence [<flags>] [<log_file>]
 *
 *   --raw         input data are raw audit records
 *    -R
 *   --filtered    input data are filtered audit ID/PID lines (default)
 *    -F
 *   --sequence    prints input sequences
 *    -S
 *   --ordered     prints ordered sequences
 *    -O
 *   --post        prints post-processed sequences
 *    -P
 *   --verbose     prints out-of-sequence messages
 *    -v
 *   --help        prints this message
 *    -?
 *   <args>...     optional arguments
 *
 *
 * Output Description:
 *
 *   <input sequence number> '%' <ordered sequence number> ':' ['+'] <start ID> ['-' <end ID>] ['<' <delta sequence number> '>']
 *
 *     A '+' <start ID> prefix character indicates one or more missing IDs.
 *
 *     - <delta sequence number> < 0: the sequence was late compared with its
 *       ordered location
 *
 *     - <delta sequence number> > 0: the sequence was early compared with
 *       its ordered location (viz., from the "future").
 *
 *     - If two consecutive input sequences have deltas of n and n+2, then
 *       the two sequences were swapped.
 *
 *     - If two consecutive input sequences have deltas of n+1 and n, then
 *       they were disrupted by the interposition of an outlier sequence.
 *
 *
 * Performance:
 *
 *   $ time java -cp . CheckIDSequence -S /data/unsorted_backdoor.txt > /data/bd_id.txt
 *   real	0m1.337s
 *   user	0m1.857s
 *   sys	0m0.250s
 *
 *   $ time java -cp . CheckIDSequence -S -R /data/backdoor.log > /data/bd_id_raw.txt
 *   real	0m22.920s
 *   user	0m22.593s
 *   sys	0m0.658s
 *
 *   $ ls -lt /data/*backdoor*
 *     52116049 /data/unsorted_backdoor.txt
 *   1669513876 /data/backdoor.log
 */

/**
 * Checks audit ID sequences for missing or out of order IDs.
 * Assumes that out of order IDs are singletons.
 */
public class CheckIDSequence
{
	protected final static PrintStream pOut   = System.out;
	protected final static Logger	   logger = Logger.getLogger (CheckIDSequence.class.getCanonicalName ());

    protected static int StreamIDBase = 0;

	/*** Application-independent code ***/

	protected static class CLIParameters
	{
		protected interface Action
		{
			/*
			 * Contract: Saves argument.  If the argument is invalid,
			 * call Error () and return false; otherwise return true.
			 */
			boolean action (String arg);
		}

		public String	form1;
		public String	form2;
		public boolean	fNeedArg;
		public String	description;
		public Action	action;

		public CLIParameters (final String form1, final String form2, final String description, final Action action, final boolean fNeedArg)
		{
			this.form1		 = form1;
			this.form2		 = form2;
			this.description = description;
			this.action		 = action;
			this.fNeedArg	 = fNeedArg;
		}
	}
	/**/

	/*** Application-specific code ***/

	static protected	   CheckIDSequence Singleton;

	protected final static int	  NO_ERR		  = 0;
	protected final static int	  NO_ERR_HELP	  = 3;
	protected final static int	  ERR_RT_EXCEPT	  = 4;
	protected final static int	  ERR_MISSING_ARG = 5;

	static protected final String RAW_HELP   = "input data are raw audit records";
	static protected final String FILT_HELP  = "input data are filtered audit ID/PID lines (default)";
	static protected final String SEQ_HELP   = "prints input sequences";
	static protected final String ORDR_HELP  = "prints ordered sequences";
	static protected final String POST_HELP  = "prints post-processed sequences";
	static protected final String VERB_HELP  = "prints out-of-sequence messages";
	static protected final String HELP_HELP  = "prints this message";
	static protected final String VARG_HELP  = "optional arguments";

	protected enum CLIOption
	{
		/*** Application-specific command line options: ***/

		// --raw -R
		// --filtered -F
		// --help -?
		// <args>...

		RAW			("--raw", "-R", RAW_HELP, new CLIParameters.Action () {

			@Override
			public boolean action (final String arg)
			{
				Singleton.fIsRaw = true;
				return true;
			}

		},	false),

		FILTERED	("--filtered", "-F", FILT_HELP, new CLIParameters.Action () {

			@Override
			public boolean action (final String arg)
			{
				Singleton.fIsRaw = false;
				return true;
			}

		},	false),

		SEQUENCE	("--sequence", "-S", SEQ_HELP, new CLIParameters.Action () {

			@Override
			public boolean action (final String arg)
			{
				Singleton.fPrintSeq = true;
				return true;
			}

		},	false),

		ORDERED		("--ordered", "-O", ORDR_HELP, new CLIParameters.Action () {

			@Override
			public boolean action (final String arg)
			{
				Singleton.fPrintOrdered = true;
				return true;
			}

		},	false),

		POST		("--post", "-P", POST_HELP, new CLIParameters.Action () {

			@Override
			public boolean action (final String arg)
			{
				Singleton.fPrintPost = true;
				return true;
			}

		},	false),

		VERBOSE		("--verbose", "-v", VERB_HELP, new CLIParameters.Action () {

			@Override
			public boolean action (final String arg)
			{
				Singleton.fIsVerbose = true;
				return true;
			}

		},	false),

		HELP		("--help", "-?", HELP_HELP, new CLIParameters.Action () {

			@Override
			public boolean action (final String arg)
			{
				ShowHelp ();
				return true;
			}

		},	false),

		_UNKNOWN_ ("<args>...", null, VARG_HELP, new CLIParameters.Action () {

			@Override
			public boolean action (final String arg)
			{
				Singleton.argList.add (arg);
				return true;
			}

		},	false),
		;

		/*** Application-independent code ***/

		static protected final Map<String, CLIOption> cliMap    = new HashMap<String, CLIOption> ();
		static protected 	   int					  maxForm1;
		static protected final int					  nSpaces   = 4;
		static protected final String				  argStr	= " <arg>";

		protected CLIParameters cliParameters;

		CLIOption (final String form1, final String form2, final String description, final CLIParameters.Action action, final boolean fNeedArg)
		{
			cliParameters = new CLIParameters (form1, form2, description, action, fNeedArg);
		}

		CLIOption (final String form1, final String form2, final String description, final CLIParameters.Action action)
		{
			this (form1, form2, description, action, true);
		}

		static {
			for (CLIOption cliOption: CLIOption.values ()) {
				CLIParameters cliParameters = cliOption.cliParameters;

				cliMap.put (cliParameters.form1, cliOption);

				int length = cliParameters.form1.length ();

				if (cliParameters.fNeedArg)
					length += argStr.length ();

				if (length > maxForm1)
					maxForm1 = length;

				if (cliParameters.form2 != null)
					cliMap.put (cliParameters.form2, cliOption);
			}
		}

		public static CLIOption cliOptionForString (final String cliStr)
		{
			CLIOption cliOption = cliMap.get (cliStr);

			return cliOption != null ? cliOption : CLIOption._UNKNOWN_;
		}

		public static void ShowCLIParameter (final CLIParameters cliParameters)
		{
			pOut.append (cliParameters.form1);

			int length = cliParameters.form1.length ();

			if (cliParameters.fNeedArg) {
				length += argStr.length ();
				pOut.append (argStr);
			}

			if (cliParameters.description != null) {
				int nPad = maxForm1 - length + nSpaces;

				while (nPad-- > 0)
					pOut.append (' ');

				pOut.append (cliParameters.description);
			}

			pOut.println ();

			if (cliParameters.form2 != null) {
				pOut.print (" " + cliParameters.form2);
				if (cliParameters.fNeedArg)
					pOut.append (argStr);
				pOut.println ();
			}
		}

		protected static void ShowHelp ()
		{
			for (CLIOption cliOption: CLIOption.values ())
				ShowCLIParameter (cliOption.cliParameters);

			Singleton.status = NO_ERR_HELP;
		}

		protected static CLIParameters cliParamsFor (final String arg)
		{
			return CLIOption.cliOptionForString (arg).cliParameters;
		}

		/**/

		/*** Application-dependent code ***/

		static protected final String DASH_DASH = "--";

		protected static void Error (final String msgStr)
		{
			logger.log (Level.SEVERE, msgStr);
		}

		protected static int processArgs (final String[] args, final int minNumArgs)
		{
			int numArgs = args.length;

			if (numArgs >= minNumArgs) {
				boolean fSawDashDash = false;

				for (int i = 0; numArgs > 0; i++, numArgs--) {
					String arg = args[i];					// accommodate UNKNOWN

					if (!fSawDashDash && DASH_DASH.equals (arg)) {
						fSawDashDash = true;
						continue;
					}

					CLIParameters cliParameters = fSawDashDash ? CLIOption._UNKNOWN_.cliParameters : cliParamsFor (arg);
					boolean		  fIsBad		= false;

					if (cliParameters.fNeedArg) {
						if (--numArgs > 0)
							arg = args[++i];
						else {
							Error (cliParameters.form1 + " missing argument");
							Singleton.status = ERR_MISSING_ARG;
							fIsBad = true;
						}
					}

					if (fIsBad || !cliParameters.action.action (arg))
						return Singleton.status;
				}
			}

			else {
				Error ("You must specify at least " + minNumArgs + " argument" + (minNumArgs > 1 ? "s" : ""));
				Singleton.status = ERR_MISSING_ARG;
				CLIOption.ShowHelp ();
			}

			return Singleton.status;
		}
	}
	/**/

    protected class IDSequence implements Comparable<IDSequence>
    {
    	protected int		 seqStart;
    	protected int		 seqEnd;
    	protected int		 seqFilledStart;

    	protected int		 streamID;
    	protected IDSequence streamPrev;
    	protected IDSequence streamNext;

    	protected int		 naturalID;
    	protected IDSequence naturalPrev;
    	protected IDSequence naturalNext;

    	protected IDSequence (final ID_PID_Pair idPair, final IDSequence streamPrev)
    	{
    		this.seqStart		= idPair.audit_id;
    		this.seqEnd			= idPair.audit_id;
    		this.seqFilledStart = idPair.audit_id;

    		this.streamID = ++StreamIDBase;

    		if (streamPrev != null)
    			streamPrev.streamNext = this;

    		this.streamPrev = streamPrev;

    		naturalSequence.add (this);
		}

    	protected boolean isInSequence (final ID_PID_Pair idPair)
    	{
    		boolean retBool = idPair.audit_id == seqEnd + 1;

    		if (retBool)
    			++seqEnd;

    		return retBool;
    	}

    	protected void fillGap (final IDSequence idSeq)
    	{
			seqFilledStart = idSeq.seqEnd + 1;	// *** Fill the gap to accommodate evaluation of misplaced segments ***
    	}

    	@Override
		public int compareTo (IDSequence idSeq)
		{
			return this.seqStart - idSeq.seqStart;
		}

		@Override
		public int hashCode ()
		{
			final int prime = 31;
			int result = 1;
			result = prime * result + naturalID;
			result = prime * result + seqEnd;
			result = prime * result + seqFilledStart;
			result = prime * result + seqStart;
			result = prime * result + streamID;
			return result;
		}

		@Override
		public boolean equals (Object obj)
		{
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass () != obj.getClass ())
				return false;
			IDSequence other = (IDSequence) obj;
			if (naturalID != other.naturalID)
				return false;
			if (seqEnd != other.seqEnd)
				return false;
			if (seqFilledStart != other.seqFilledStart)
				return false;
			if (seqStart != other.seqStart)
				return false;
			if (streamID != other.streamID)
				return false;
			return true;
		}

		protected StringBuffer append (final StringBuffer sBuff)
		{
			sBuff.append (streamID)
				 .append ("%")
				 .append (naturalID)
				 .append (":");
			if (seqStart > seqFilledStart)
				sBuff.append ("+");
			sBuff.append (seqStart);

			boolean fStartIsEnd = seqStart == seqEnd;
			boolean fIsNatural	= streamID == naturalID;

			if (!fStartIsEnd)
				sBuff.append ("-")
					 .append (seqEnd);
			if (!fIsNatural)
				sBuff.append ("<")
					 .append (naturalID - streamID)
					 .append (">");

			return sBuff;
		}

		@Override
		public String toString ()
		{
			return append (new StringBuffer ()).toString ();
		}

    }

    protected static class ID_PID_Pair
    {
    	protected static final Pattern rawPatt	= Pattern.compile ("^type=SYSCALL.+\\.\\d{3}:(\\d+).+ pid=(\\d+).+$");
    	protected static final Pattern filtPatt = Pattern.compile ("(\\d+)\\s+(\\d+)");
    	protected static	   Pattern mPatt;

    	protected static void SetPattern (final boolean fIsRaw)
    	{
    		mPatt = fIsRaw ? rawPatt : filtPatt;
    	}

    	protected int	  audit_id;
    	protected int	  pid;
    	protected boolean fIsGood;

    	protected ID_PID_Pair (final String aStr)
    	{
    		Matcher matcher = mPatt.matcher (aStr);

    		fIsGood = matcher.matches ();

    		if (fIsGood) {
				audit_id = Integer.valueOf (matcher.group (1));
				pid	     = Integer.valueOf (matcher.group (2));
    		}
    	}

    	protected boolean isGood ()
    	{
    		return fIsGood;
    	}

    	@Override
    	public String toString ()
    	{
    		return audit_id + ":" + pid;
    	}
    }

	protected List<String>	  argList;
	protected int			  status;
	protected boolean		  fIsVerbose;
	protected boolean		  fIsRaw;
	protected boolean		  fPrintSeq;
	protected boolean		  fPrintOrdered;
	protected boolean		  fPrintPost;
    protected IDSequence	  firstSequence;
    protected IDSequence	  lastSequence;
    protected Set<IDSequence> naturalSequence;

    protected CheckIDSequence (final String [] args)
    {
    	argList			= new ArrayList<String> ();
    	naturalSequence = new TreeSet<IDSequence> ();

    	Singleton = this;

    	// Process command line options
    	CLIOption.processArgs (args, 0);

    	ID_PID_Pair.SetPattern (fIsRaw);
    }

    protected void processInput (final InputStream inStream) throws Exception
    {
    	BufferedReader buffIn = new BufferedReader (new InputStreamReader (inStream));
    	String		   aStr	  = null;

    	while ((aStr = buffIn.readLine ()) != null) {
    		ID_PID_Pair idPair = new ID_PID_Pair (aStr);

    		if (idPair.isGood ()) {
    			if (lastSequence == null || !lastSequence.isInSequence (idPair)) {
    				lastSequence = new IDSequence (idPair, lastSequence);

    				if (firstSequence == null)
    					firstSequence = lastSequence;
    			}
    		}
    	}

    	if (inStream != System.in)
    		buffIn.close ();

    	// Define natural ordering indices and previous/next references

    	int		   naturalIDBase = 0;
    	IDSequence lastSeq		 = null;

    	for (IDSequence idSeq: naturalSequence) {
    		idSeq.naturalID	  = ++naturalIDBase;
    		idSeq.naturalPrev = lastSeq;

    		if (lastSeq != null)
    			lastSeq.naturalNext = idSeq;

    		lastSeq = idSeq;
    	}
    }

    protected void reportInputSequences ()
    {
    	if (fPrintSeq) {
    		pOut.println ("Input Sequences:");

    		for (IDSequence idSeq = firstSequence; idSeq != null; idSeq = idSeq.streamNext)
    			pOut.println ("  " + idSeq.toString ());

    		pOut.println ();
    	}
    }

    protected void reportOrderedSequences ()
    {
    	if (fPrintOrdered) {
    		pOut.println ("Ordered Sequences:");

    		for (IDSequence idSeq: naturalSequence)
    			pOut.println ("  " + idSeq.toString ());

    		pOut.println ();
    	}
    }

    protected int reportMissing ()
    {
    	IDSequence	 lastSeq = null;
    	StringBuffer sBuff	 = new StringBuffer ();

    	for (IDSequence idSeq: naturalSequence) {
    		if (lastSeq != null) {
    			int iGapStart = lastSeq.seqEnd + 1;
    			int iGapEnd	  = idSeq.seqStart - 1;
    			
    			if (iGapStart <= iGapEnd) {
    				if (sBuff.length () == 0)
    					sBuff.append ("Missing IDs:");

    				sBuff.append (" ")
    					 .append (iGapStart);
    				
    				if (iGapStart < iGapEnd)
    					sBuff.append ("-")
    						 .append (iGapEnd);
    			}

    			idSeq.fillGap (lastSeq);
    		}

    		lastSeq = idSeq;
    	}

    	int retVal = sBuff.length () > 0 ? 1 : 0;

    	if (retVal == 1)
    		pOut.println (sBuff.toString ());

    	return retVal;
    }

    protected int reportMisplaced ()
    {
    	StringBuffer sBuff = new StringBuffer ();

    	for (IDSequence currSeq: naturalSequence) {
        	IDSequence prevSeq = currSeq.streamPrev;

        	if (prevSeq != null) {
    			if (currSeq.seqFilledStart != prevSeq.seqEnd + 1) {
    				if (fIsVerbose) {
	    				if (sBuff.length () == 0)
	    					sBuff.append ("Misplaced IDs:");
	    				sBuff.append (" ");

	    				currSeq.append (sBuff)
	    					   .append ("[")
	    					   .append (currSeq.streamID - prevSeq.streamID)
	    					   .append (".")
	    					   .append (currSeq.seqStart - prevSeq.seqEnd)
	    					   .append ("]");
    				}

    				// Snip this sequence and repair references in previous and next

    				IDSequence nextSeq = currSeq.streamNext;

    				prevSeq.streamNext = nextSeq;

    				if (nextSeq != null)
    					nextSeq.streamPrev = prevSeq;

    				// Insert this sequence into its "natural" location

    				IDSequence naturalPrev = currSeq.naturalPrev;

    				if (naturalPrev != null) {
    					IDSequence naturalPrevNext = naturalPrev.streamNext;

    					naturalPrev.streamNext = currSeq;
    					currSeq.streamPrev	   = naturalPrev;
    					currSeq.streamNext	   = naturalPrevNext;

    					if (naturalPrevNext != null)
    						naturalPrevNext.streamPrev = currSeq;
    				}
    			}
    		}
    	}

    	int retVal = sBuff.length () > 0 ? 1 : 0;

    	if (retVal == 1)
    		pOut.println (sBuff.toString ());

    	return retVal;
    }

    protected void reportPostSequences ()
    {
    	if (fPrintPost) {
    		pOut.println ("Post Sequences:");

    		for (IDSequence idSeq = firstSequence; idSeq != null; idSeq = idSeq.streamNext)
    			pOut.println ("  " + idSeq.toString ());

    		pOut.println ();
    	}
    }

    protected int run () throws Exception
    {
    	if (status != NO_ERR)
    		return status != NO_ERR_HELP ? status : NO_ERR;

		InputStream inStream = argList.size () > 0 ? new FileInputStream (argList.get (0)) : System.in;

    	processInput (inStream);

    	reportInputSequences ();
    	reportOrderedSequences ();

    	int retVal = reportMissing () + reportMisplaced ();

    	reportPostSequences ();

    	return retVal;
    }

    public static void main (String [] args)
    {
    	int status = 0;

    	try {
			CheckIDSequence chkAuditSeq = new CheckIDSequence (args);

			status = chkAuditSeq.run ();
		} catch (Exception e) {
			pOut.println (e);
			status = ERR_RT_EXCEPT;
		}

    	System.exit (status);
    }

}

//Local Variables:
//tab-width: 4
//End:
