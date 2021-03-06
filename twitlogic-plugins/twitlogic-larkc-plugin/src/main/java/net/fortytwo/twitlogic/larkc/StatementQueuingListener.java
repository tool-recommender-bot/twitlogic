package net.fortytwo.twitlogic.larkc;

import net.fortytwo.twitlogic.TwitLogic;
import net.fortytwo.twitlogic.util.properties.PropertyException;
import org.openrdf.model.Statement;
import org.openrdf.sail.SailConnectionListener;

import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.logging.Logger;

/**
 * @author Joshua Shinavier (http://fortytwo.net)
 */
public class StatementQueuingListener implements SailConnectionListener {

    private static final Logger LOGGER = TwitLogic.getLogger(StatementQueuingListener.class);

    private final ArrayBlockingQueue<Statement> queue;
    private final StreamingPlugin.OverflowPolicy policy;

    public StatementQueuingListener(final ArrayBlockingQueue<Statement> queue,
                                    final StreamingPlugin.OverflowPolicy policy) throws PropertyException {
        this.queue = queue;
        this.policy = policy;
    }

    public void statementAdded(final Statement statement) {
        switch (policy) {
            case DROP_OLDEST:
                while (!queue.offer(statement)) {
                    try {
                        queue.take();
                    } catch (InterruptedException e) {
                        LOGGER.severe("thread interrupted while removing from Twitter statement queue");
                    }
                }
                break;
            case DROP_MOST_RECENT:
                queue.offer(statement);
                break;
        }
    }

    public void statementRemoved(final Statement statement) {
        // Do nothing.
    }

    public ArrayBlockingQueue<Statement> getQueue() {
        return queue;
    }
}
