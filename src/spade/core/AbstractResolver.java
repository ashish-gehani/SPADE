package spade.core;

import org.apache.commons.collections.CollectionUtils;
import spade.reporter.audit.OPMConstants;

import javax.net.ssl.SSLSocket;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import static spade.core.AbstractQuery.DEFAULT_MAX_LIMIT;
import static spade.core.AbstractQuery.OPERATORS;
import static spade.core.AbstractStorage.PRIMARY_KEY;

/**
 * @author raza
 */
public abstract class AbstractResolver implements Runnable
{
    public static final String SOURCE_HOST = "source_host";
    public static final String SOURCE_PORT = "source_port";
    public static final String DESTINATION_HOST = "destination_host";
    public static final String DESTINATION_PORT = "destination_port";

    // fields required to fetch and return remote parts of result graph
    protected Set<Graph> finalGraph = new HashSet<>();
    protected Graph partialGraph;
    protected int depth;
    protected String direction;
    protected String function;

    protected AbstractResolver(Graph partialGraph, String function, int depth, String direction)
    {
        this.partialGraph = partialGraph;
        this.function = function;
        this.depth = depth;
        this.direction = direction;
    }

    public Set<Graph> getFinalGraph()
    {
        return finalGraph;
    }

    @Override
    public abstract void run();
}

