package net.fortytwo.twitlogic.persistence;

import net.fortytwo.twitlogic.TwitLogic;
import net.fortytwo.twitlogic.flow.Handler;
import net.fortytwo.twitlogic.model.Dollartag;
import net.fortytwo.twitlogic.model.Hashtag;
import net.fortytwo.twitlogic.model.Person;
import net.fortytwo.twitlogic.model.PlainLiteral;
import net.fortytwo.twitlogic.model.Resource;
import net.fortytwo.twitlogic.model.Triple;
import net.fortytwo.twitlogic.model.Tweet;
import net.fortytwo.twitlogic.model.TypedLiteral;
import net.fortytwo.twitlogic.model.URIReference;
import net.fortytwo.twitlogic.model.User;
import net.fortytwo.twitlogic.persistence.beans.Feature;
import net.fortytwo.twitlogic.persistence.beans.MicroblogPost;
import net.fortytwo.twitlogic.persistence.beans.Point;
import net.fortytwo.twitlogic.persistence.beans.SpatialThing;
import net.fortytwo.twitlogic.services.twitter.HandlerException;
import net.fortytwo.twitlogic.services.twitter.TwitterClient;
import net.fortytwo.twitlogic.services.twitter.TwitterClientException;
import net.fortytwo.twitlogic.util.properties.PropertyException;
import org.openrdf.concepts.owl.Thing;
import org.openrdf.model.Literal;
import org.openrdf.model.Statement;
import org.openrdf.model.URI;
import org.openrdf.model.Value;
import org.openrdf.model.ValueFactory;
import org.openrdf.sail.SailException;

import javax.xml.namespace.QName;
import java.util.Set;
import java.util.logging.Logger;

/**
 * @author Joshua Shinavier (http://fortytwo.net).
 */
public class TweetPersister implements Handler<Tweet> {
    private static final Logger LOGGER = TwitLogic.getLogger(TweetPersister.class);

    private final TweetStoreConnection storeConnection;
    private final ValueFactory valueFactory;
    private final PersistenceContext persistenceContext;
    private final PlacePersistenceHelper placeHelper;

    /**
     * @param store  the knowledge base
     * @param client an optional Twitter client for building the Places hierarchy
     * @throws TweetStoreException if something goes awry
     */
    public TweetPersister(final TweetStore store,
                          final TwitterClient client) throws TweetStoreException, TwitterClientException {
        this.storeConnection = store.createConnection();
        this.valueFactory = store.getSail().getValueFactory();
        try {
            this.persistenceContext = new PersistenceContext(storeConnection);
        } catch (PropertyException e) {
            throw new TweetStoreException(e);
        }
        this.placeHelper = null == client
                ? null
                : new PlacePersistenceHelper(persistenceContext, client);
    }

    public void close() throws TweetStoreException {
        storeConnection.close();
    }

    public boolean isOpen() {
        return true;
    }

    // Note: synchronization may be necessary for use with Twitter4j
    public synchronized void handle(final Tweet tweet) throws HandlerException {
        LOGGER.fine(tweet.describe());

        storeConnection.getElmoManager().getTransaction().begin();
        boolean finished = false;

        try {
            // begin Elmo operations

            if (null != placeHelper) {
                // Since Elmo is not thread-safe, Elmo operations to be carried out by
                // placeHelper are queued until they can be executed here, in the main
                // transaction.
                placeHelper.flush();
            }

            boolean hasAnnotations = 0 < tweet.getAnnotations().size();

            MicroblogPost currentMicroblogPost = persistenceContext.persist(tweet, hasAnnotations);

            if (null != tweet.getUser()) {
                persistenceContext.persist(tweet.getUser());
            } else {
                LOGGER.fine("null user for tweet: " + tweet);
            }

            if (null != tweet.getGeo()) {
                Point p = persistenceContext.persist(tweet.getGeo());

                Set<SpatialThing> s = currentMicroblogPost.getLocation();
                s.add(p);
                currentMicroblogPost.setLocation(s);
            }

            if (null != tweet.getPlace()) {
                Feature f = persistenceContext.persist(tweet.getPlace());
                if (null != placeHelper) {
                    try {
                        placeHelper.submit(tweet.getPlace(), f);
                    } catch (TwitterClientException e) {
                        throw new HandlerException(e);
                    }
                }

                Set<SpatialThing> s = currentMicroblogPost.getLocation();
                s.add(f);
                currentMicroblogPost.setLocation(s);
            }

            // end Elmo operations

            try {
                storeConnection.commit();
            } catch (TweetStoreException e) {
                throw new HandlerException(e);
            }

            // Note: we assume that Twitter and any other services which supply these posts will not allow a cycle
            // of replies and/or retweets.
            // Note: these tweets are persisted in their own transactions.
            if (null != tweet.getInReplyToTweet()) {
                this.handle(tweet.getInReplyToTweet());
            }
            if (null != tweet.getRetweetOf()) {
                this.handle(tweet.getRetweetOf());
            }

            //System.out.println("    ...ending transaction");

            // Note: these Sail operations are performed outside of the Elmo transaction.  If they were to be
            // carried out inside the transaction, apparently Sesame would kill the thread without throwing
            // an exception or logging an error.
            if (hasAnnotations) {
                for (Triple triple : tweet.getAnnotations()) {
                    System.out.println("\t (" + triple.getWeight() + ")\t" + triple);
                    Statement st;

                    try {
                        st = toRDF(triple, uriOf(currentMicroblogPost.getEmbedsKnowledge()));
                    } catch (TwitterClientException e) {
                        throw new HandlerException(e);
                    }

                    if (null != st) {
                        // FIXME: creating a statement and then breaking it into parts is wasty
                        try {
                            //System.out.println("subject: " + st.getSubject());
                            //System.out.println("predicate: " + st.getPredicate());
                            //System.out.println("object: " + st.getObject());
                            //System.out.println("context: " + st.getContext());
                            storeConnection.getSailConnection()
                                    .addStatement(st.getSubject(), st.getPredicate(), st.getObject(), st.getContext());
                        } catch (SailException e) {
                            throw new HandlerException(e);
                        }
                    }
                }

                try {
                    storeConnection.getSailConnection().commit();
                } catch (SailException e) {
                    throw new HandlerException(e);
                }
            }

            finished = true;
        } catch (HandlerException e) {
            throw e;
        } catch (Throwable t) {
            throw new HandlerException(t);
        } finally {
            if (!finished) {
                try {
                    LOGGER.info("rolling back failed transaction");
                    storeConnection.rollback();
                } catch (TweetStoreException e) {
                    throw new HandlerException(e);
                }
            }
        }
    }

    private Statement toRDF(final Triple triple,
                            final org.openrdf.model.Resource graph) throws TwitterClientException {
        Value subject = toRDF(triple.getSubject());
        Value predicate = toRDF(triple.getPredicate());
        Value object = toRDF(triple.getObject());

        if (!(subject instanceof org.openrdf.model.Resource)) {
            LOGGER.warning("triple subject is not a subject resource: " + triple.getSubject());
            return null;
        } else if (!(predicate instanceof URI)) {
            LOGGER.warning("triple predicate is not a predicate resource: " + triple.getPredicate());
            return null;
        } else {
            return valueFactory.createStatement((org.openrdf.model.Resource) subject, (URI) predicate, object, graph);
        }
    }

    private Value toRDF(final Resource resource) throws TwitterClientException {
        switch (resource.getType()) {
            case DOLLARTAG:
                return valueOf((Dollartag) resource);
            case HASHTAG:
                return valueOf((Hashtag) resource);
            case PLAIN_LITERAL:
                return valueOf((PlainLiteral) resource);
            case TWEET:
                return valueOf((Tweet) resource);
            case TYPED_LITERAL:
                return valueOf((TypedLiteral) resource);
            case URI_REFERENCE:
                return valueOf((URIReference) resource);
            case USER:
                return valueOf((User) resource);
            case PERSON:
                return valueOf((Person) resource);
            default:
                throw new IllegalStateException("unhandled resource type: " + resource.getType());
        }
    }

    private URI valueOf(final Dollartag tag) {
        return uriOf(persistenceContext.persist(tag));
    }

    private URI valueOf(final Hashtag hashtag) {
        return uriOf(persistenceContext.persist(hashtag));
    }

    private Literal valueOf(final PlainLiteral literal) {
        return valueFactory.createLiteral(literal.getLabel());
    }

    private URI valueOf(final Tweet tweet) {
        return uriOf(persistenceContext.persist(tweet, 0 < tweet.getAnnotations().size()));
    }

    private Literal valueOf(final TypedLiteral literal) {
        return valueFactory.createLiteral(literal.getLabel(),
                valueFactory.createURI(literal.getDatatype()));
    }

    private URI valueOf(final URIReference uri) throws TwitterClientException {
        //String nonRedirecting = httpClient.resolve301Redirection(uri.getValue());
        //return valueFactory.createURI(nonRedirecting);

        // TODO: consider bringing 301 resolution back.
        return valueFactory.createURI(uri.getValue());
    }

    private URI valueOf(final User user) throws TwitterClientException {
        return uriOf(persistenceContext.persist(user));
    }

    private URI valueOf(final Person person) throws TwitterClientException {
        return uriOf(persistenceContext.persist(person));
    }

    private URI uriOf(final Thing thing) {
        QName q = thing.getQName();
        return valueFactory.createURI(q.getNamespaceURI() + q.getLocalPart());
    }

}
