/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2026 SRI International
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

package spade.core.analyzer.command;

import java.io.Serializable;
import java.util.logging.Level;
import java.util.logging.Logger;

import spade.core.AbstractStorage;
import spade.core.AbstractTransformer;
import spade.core.Graph;
import spade.core.Kernel;
import spade.core.analyzer.QueryableStorage;
import spade.core.analyzer.RequiredConfig;
import spade.core.analyzer.command.execution.Context;
import spade.core.exception.StorageNotQueryable;
import spade.core.analyzer.command.exception.CommandFailure;
import spade.core.analyzer.command.exception.ServerFailure;
import spade.core.analyzer.command.exception.UnexpectedFailure;
import spade.query.quickgrail.QuickGrailExecutor;
import spade.query.quickgrail.core.GraphStatistic;
import spade.query.quickgrail.core.QueryInstructionExecutor;
import spade.utility.Result;

/*
    QuickGrail command.
*/
public class QuickGrail extends AbstractCommand {

    private final Logger logger = Logger.getLogger(this.getClass().getName());

    public QuickGrail(final Type type, final String raw)
        throws IllegalArgumentException {
        super(type, raw);
    }

    public static QuickGrail create(final String raw)
        throws ServerFailure, CommandFailure {
        if (raw == null) {
            throw new IllegalArgumentException("Raw query command cannot be null");
        }
        final String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            throw new CommandFailure(
                "Invalid command syntax. Expected: " + "Non-empty string" + ". Actual: " + raw
            );
        }
        final QuickGrail instance = new QuickGrail(Type.QUICKGRAIL, raw);
        return instance;
    }

    @Override
    protected Serializable executeInternal(
        final Context ctx
    ) throws CommandFailure, ServerFailure, UnexpectedFailure {
        if (ctx == null) {
            throw new ServerFailure("Execution context cannot be null");
        }

        final QueryableStorage qStorage = ctx.getStorage();
        if (qStorage == null) {
            throw new ServerFailure("No queryable storage set for querying");
        }

        final AbstractStorage storage = qStorage.getStorage();
        if (storage == null) {
            throw new ServerFailure("NULL storage set for querying");
        }

        QueryInstructionExecutor qie = null;
        try {
            qie = storage.getQueryInstructionExecutor();
        } catch (StorageNotQueryable e) {
            throw new ServerFailure("Storage set for querying is not queryable", e);
        }

        if (qie == null) {
            throw new ServerFailure("Null query instruction executor for current storage");
        }

        final spade.query.execution.Context queryExecCtx = 
            new spade.query.execution.Context(qie);
        try{
            final QuickGrailExecutor qgExec = ctx.getQuickGrailExecutor();
            if (qgExec == null) {
                throw new ServerFailure("Null quickgrail executor in context");
            }
            final Serializable result = qgExec.execute(getRaw(), queryExecCtx);

            final Serializable processedResult = processResult(
                ctx, queryExecCtx, result
            );

            return processedResult;
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to execute quickgrail command", e);
            throw e;
        } finally {
            queryExecCtx.destroy();
        }
    }

    private final Serializable processResult(
        final Context cmdExecCtx,
        final spade.query.execution.Context queryExecCtx,
        final Serializable result
    ) throws CommandFailure, ServerFailure, UnexpectedFailure {
        if (cmdExecCtx == null) {
            throw new ServerFailure(
                "Failed to process result. NULL cmd execution context"
            );
        }

        if (queryExecCtx == null) {
            throw new ServerFailure(
                "Failed to process result. NULL query execution context"
            );
        }

        if (result == null) {
            return result;
        }

		if (result instanceof spade.core.Graph) {
			return processResultGraph(
                cmdExecCtx,
                queryExecCtx,
                (spade.core.Graph)result
            );
		} else if (result instanceof spade.query.quickgrail.core.GraphStatistic) {
			return processResultGraphStatistic(
                cmdExecCtx,
                queryExecCtx,
                (GraphStatistic)result
			);
		} else {
			return result;
		}
    }

    private final synchronized Graph processResultGraph(
        final Context cmdExecCtx,
        final spade.query.execution.Context queryExecCtx,
        final Graph result
	) throws ServerFailure, CommandFailure, UnexpectedFailure {
        if (cmdExecCtx == null) {
            throw new ServerFailure(
                "Failed to process result. NULL cmd execution context"
            );
        }

        if (queryExecCtx == null) {
            throw new ServerFailure(
                "Failed to process result. NULL query execution context"
            );
        }

        if (result == null) {
            throw new ServerFailure(
                "Failed to process result. NULL result"
            );
        }

        final RequiredConfig cfg = cmdExecCtx.getRequiredConfig();
        if (cfg == null) {
            throw new ServerFailure(
                "Failed to process result. NULL analyzer config"
            );
        }

        final Boolean useTransformer = cfg.getUseTransformer();
        if (useTransformer == null || !useTransformer) {
            return result;
        }

        final Graph transformedGraph = iterateTransformers(result, queryExecCtx);
        transformedGraph.setHostName(Kernel.getHostName());
        return transformedGraph;
        // TODO
        // transformedGraph.addSignature(spadeQuery.queryNonce);
	}

	private final synchronized GraphStatistic processResultGraphStatistic(
        final Context cmdExecCtx,
        final spade.query.execution.Context queryExecCtx,
        final GraphStatistic result
    ) throws ServerFailure, CommandFailure, UnexpectedFailure {
        if (cmdExecCtx == null) {
            throw new ServerFailure(
                "Failed to process result. NULL execution context"
            );
        }

        if (queryExecCtx == null) {
            throw new ServerFailure(
                "Failed to process result. NULL query execution context"
            );
        }

        if (result == null) {
            throw new ServerFailure(
                "Failed to process result. NULL result"
            );
        }

        final RequiredConfig cfg = cmdExecCtx.getRequiredConfig();
        if (cfg == null) {
            throw new ServerFailure(
                "Failed to process result. NULL analyzer config"
            );
        }

        final Double epsilon = cfg.getEpsilon();
        if (epsilon == null || epsilon < 0) {
			return result;
        }

        try {
            result.privatize(epsilon);
            return result;
        } catch (Exception e) {
            throw new ServerFailure("Failed to privatize graph statistics", e);
        }
	}

    private final synchronized Graph iterateTransformers(
        final Graph graph, final spade.query.execution.Context queryExecContext
    ) throws ServerFailure, UnexpectedFailure {
        Graph transformedGraph = graph;

        synchronized(Kernel.transformers){
            for(int i = 0; i < Kernel.transformers.size(); i++){
                try{
                    final AbstractTransformer transformer = Kernel.transformers.get(i);
                    final Result<Graph> executeResult = 
                        AbstractTransformer.executeWithQueryContext(
                            transformer, transformedGraph, queryExecContext
                        );
                    if(executeResult.error){
                        throw new RuntimeException(
                            executeResult.errorMessage, executeResult.exception
                        );
                    }
                    transformedGraph = executeResult.result;
                }catch(Exception e){
                    final AbstractTransformer transformer = Kernel.transformers.get(i);
                    final String transformerName = (
                        transformer == null
                        ? "<NULL transformer>"
                        : transformer.getClass().getSimpleName()
                    );
                    throw new ServerFailure(
                        "Failed to apply transformer "
                        + "'" + transformerName + "'",
                        e
                    );
                }
            }
        }
        return transformedGraph;
    }
}
