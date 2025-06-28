/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2025 SRI International

 This program is free software: you can redistribute it and/or
 modify it under the terms of the GNU General Public License as
 published by the Free Software Foundation, either version 3 of the
 License, or (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program. If not, see <http://www.gnu.org/licenses/>.
 --------------------------------------------------------------------------------
 */

package spade.reporter.audit.bpf.ameba;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.logging.FileHandler;
import java.util.logging.SimpleFormatter;

import org.json.JSONObject;

import spade.reporter.audit.AuditConfiguration;
import spade.reporter.audit.ProcessUserSyscallFilter;
import spade.utility.FileUtility;
import spade.utility.HelperFunctions;

public class Process {

	private final Logger logger = Logger.getLogger(Process.class.getName());

    private final Arguments args;

	private final Config config;

	private final ProcessState state = new ProcessState();

	public Process(
		final Arguments args,
		final Config config
	) throws Exception {
        this.args = args;
		this.config = config;
		initLogger();
	}

	private void initLogger() {
		if (config.getAmebaLogPath() != null) {
			try {
				final boolean append = false;
				final boolean useParentHandlers = false;
				final FileHandler fileHandler = new FileHandler(config.getAmebaLogPath(), append);
				fileHandler.setFormatter(new SimpleFormatter());
				logger.addHandler(fileHandler);
				logger.setUseParentHandlers(useParentHandlers);
			} catch (Exception e) {
				logger.warning("Failed to set up file for logger. Defaulting to main log. Error: " + e.getMessage());
			}
		}
	}

	public Config getAmebaConfig() {
		return this.config;
	}

	public Arguments getAmebaArguments() {
		return this.args;
	}

	/**
	 * Start the process.
	 * 
	 * @throws Exception If failed to start
	 */
    public void start() throws Exception {
		synchronized (state) {
			if (isRunning())
				throw new RuntimeException("Already in running state");

			final String command = String.join(
				" ",
				this.config.getAmebaBinPath(),
				this.args.buildArgumentString()
			);
			try {
				this.state.process = Runtime.getRuntime().exec(command);
			} catch (Exception e) {
				throw new Exception("Failed to start process using command: " + command, e);
			}
			try {
				final BufferedReader stderrReader = new BufferedReader(new InputStreamReader(this.state.process.getErrorStream()));
				final BufferedReader stdoutReader = new BufferedReader(new InputStreamReader(this.state.process.getInputStream()));
				// Read the initial messages to get the pid or any errors that prevent a process start.
				waitUntilOperationalAndSetPid(stdoutReader);

				this.state.streamReadersService = Executors.newFixedThreadPool(2);
				this.state.streamReadersService.submit(() -> logStream(stdoutReader, "[STDOUT]"));
				this.state.streamReadersService.submit(() -> logStream(stderrReader, "[STDERR]"));
			} catch (Exception e) {
				stop();
				throw e;
			}
		}
    }

	private void logStream(final BufferedReader reader, String prefix) {
        try {
            String line;
            while ((line = reader.readLine()) != null) {
				if (this.config.isVerbose()) {
					logger.info(prefix + " " + line);
					continue;
				}

				// If not verbose then log only select messages.

				JSONObject jsonObj = null;
				try {
					jsonObj = new JSONObject(line);
				} catch (Exception e) {
					logger.log(Level.WARNING, "Expected JSON output but got: " + line, e);
					continue;
				}

				LogMsg amebaLogMsg = null;
				try {
					amebaLogMsg = LogMsg.fromJson(jsonObj);
				} catch (Exception e) {
					logger.log(Level.WARNING, "Expected structured log msg but got: " + line, e);
					continue;
				}

				AppState appState = amebaLogMsg.getState();
				if (
					appState == AppState.APP_STATE_OPERATIONAL_WITH_ERROR ||
					appState == AppState.APP_STATE_STOPPED_WITH_ERROR
				) {
                	logger.info(prefix + " " + line);
				}
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, prefix + " stream closed or errored out.", e);
        } catch (Exception e) {
			logger.log(Level.WARNING, prefix + " stream error.", e);
		}
    }

	public boolean isRunning() {
		synchronized (state) {
			return state.process != null && state.process.isAlive();
		}
	}

	public int getPid() {
		synchronized (state) {
			return state.pid;
		}
	}

	private void waitUntilOperationalAndSetPid(final BufferedReader stdoutReader) throws Exception {
		synchronized (state) {
			if (!isRunning())
				throw new RuntimeException("A stopped process cannot become operational");
			final long timeoutMillis = 60 * 1000; 
			final long lineReadTimeoutMillis = 5 * 1000;
			final long startTimeMillis = System.currentTimeMillis();
			while (true) {
				if (System.currentTimeMillis() - startTimeMillis >= timeoutMillis) {
					throw new RuntimeException(
						"Failed to become operational in reasonable time. Timeout: " + (timeoutMillis / 1000) + " seconds."
					);
				}

				String line = null;
				try {
					line = FileUtility.readLineWithTimeout(stdoutReader, lineReadTimeoutMillis);
				} catch (InterruptedException ie) {
					Thread.currentThread().interrupt();
					throw new RuntimeException("Failed to become operational because of interrupt", ie);
				} catch (TimeoutException te) {
					// Ignore this exception
					continue;
				} catch (Exception e) {
					logger.log(Level.WARNING, "Unexpected error in read line", e);
					continue;
				}

				if (line == null) {
					throw new RuntimeException("Reached end of output unexpectedly");
				}

				JSONObject jsonObj = null;
				try {
					jsonObj = new JSONObject(line);
				} catch (Exception e) {
					logger.log(Level.WARNING, "Expected JSON output but got: " + line, e);
					continue;
				}

				LogMsg amebaLogMsg = null;
				try {
					amebaLogMsg = LogMsg.fromJson(jsonObj);
				} catch (Exception e) {
					logger.log(Level.WARNING, "Expected structured log msg but got: " + line, e);
					continue;
				}

				AppState appState = amebaLogMsg.getState();
				if (appState.isStopped()) {
					logger.log(Level.WARNING, "Process stopped unexpectedly. Message: " + amebaLogMsg.toString());
					throw new RuntimeException("Process stopped unexpectedly");
				}

				if (this.config.isVerbose()) {
					logger.log(Level.INFO, amebaLogMsg.toString());
				}

				if (appState == AppState.APP_STATE_OPERATIONAL_PID) {
					try {
						int pid = amebaLogMsg.getPid();
						this.state.pid = pid;
						break;
					} catch (Exception e) {
						throw new RuntimeException("Failed to get pid from: " + amebaLogMsg.toString(), e);
					}
				} else {
					continue;
				}
			}
		}
	}

	/**
	 * Stop process, and cleanup state.
	 * 
	 * @return True if stopped successfully or not running
	 */
	public boolean stop(){
		synchronized (state) {
			boolean stoppedSuccessfully;
			if (isRunning()) {
				final int signal_sigterm = 15;
				final Integer pid = this.state.pid;

				boolean signalingFailed = false;
				try{
					HelperFunctions.nixSendSignalToPid(String.valueOf(pid), signal_sigterm);
					stoppedSuccessfully = true;
				}catch(Exception e){
					signalingFailed = true;
					stoppedSuccessfully = false;
					logger.log(Level.WARNING, "Failed to stop process with pid '" + pid + "' using signal '" + signal_sigterm + "'", e);
				}
				if (signalingFailed) {
					try {
						this.state.process.destroyForcibly();
						this.state.process.waitFor(10, TimeUnit.SECONDS);
						stoppedSuccessfully = true;
					} catch (Exception e) {
						stoppedSuccessfully = false;
						logger.log(Level.WARNING, "Failed to cleanly stop", e);
					}
				}

				this.state.process = null;
				this.state.pid = 0;

				try {
					this.state.streamReadersService.shutdownNow();
				} catch (Exception e) {
					// ignore
				} finally {
					this.state.streamReadersService = null;
				}
			} else {
				stoppedSuccessfully = true;
			}
			return stoppedSuccessfully;
		}
	}

	//

	private class ProcessState {
		private java.lang.Process process = null;
		private int pid;
		private ExecutorService streamReadersService;
	}

	public static Process create(
		final boolean isLiveMode,
		final Config amebaConfig,
		final Arguments amebaArguments,
		final AuditConfiguration auditConfig,
		final ProcessUserSyscallFilter processUserSyscallFilter
	) throws Exception {
        final Process ap = new Process(amebaArguments, amebaConfig);
        return ap;
    }
}
