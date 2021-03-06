package net.fortytwo.twitlogic.util.misc;

import org.openrdf.model.Literal;
import org.openrdf.model.URI;
import org.openrdf.model.Value;
import org.openrdf.query.BindingSet;
import org.openrdf.query.QueryLanguage;
import org.openrdf.query.TupleQuery;
import org.openrdf.query.TupleQueryResult;
import org.openrdf.repository.Repository;
import org.openrdf.repository.RepositoryConnection;
import org.openrdf.repository.sail.SailRepository;
import org.openrdf.rio.RDFFormat;
import org.openrdf.sail.Sail;
import org.openrdf.sail.nativerdf.NativeStore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;

/**
 * @author Joshua Shinavier (http://fortytwo.net).
 */
public class CSVExporter {
    public static void main(final String[] args) {
        try {
            new CSVExporter().doit();
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }


    /*
PREFIX dc: <http://purl.org/dc/terms/>
PREFIX sioc: <http://rdfs.org/sioc/ns#>
SELECT DISTINCT ?tweet ?screenName ?replyTo ?createdAt ?text WHERE {
    ?tweet dc:created ?createdAt .
    ?tweet sioc:content ?text .
    ?tweet sioc:has_creator ?account .
    ?account sioc:id ?screenName .
    OPTIONAL { ?tweet sioc:reply_to ?replyTo . } .
}
     */
    private void doit() throws Exception {
        String query;
        OutputStream os;

        String[] names = new String[]{"tweet", "screenName", "replyTo", "createdAt", "text"};

        File dir = new File("/tmp/sparqljdbc/nativestore");
        Sail sail = new NativeStore(dir);
        sail.initialize();
        try {
            Repository repo = new SailRepository(sail);
            RepositoryConnection rc = repo.getConnection();
            try {
                rc.begin();
                rc.clear();
                rc.commit();
                rc.begin();

                rc.add(new File("/tmp/twitlogic-dump.nq"), "", RDFFormat.NQUADS);
                rc.commit();
                rc.begin();

                ////////////////////////////////////////////////////////////////

                query = "PREFIX dc: <http://purl.org/dc/terms/>\n" +
                        "PREFIX sioc: <http://rdfs.org/sioc/ns#>\n" +
                        "SELECT DISTINCT ?tweet1 ?tweet2 ?time1 ?time2 WHERE {\n" +
                        "    ?tweet1 dc:created ?time1 .\n" +
                        "    ?tweet2 dc:created ?time2 .\n" +
                        "    ?tweet2 sioc:reply_of ?tweet1 .\n" +
                        "}";

                os = new FileOutputStream("/tmp/replies.csv");
                try {
                    doQuery(query, rc, new PrintStream(os), new CSVOutputter() {
                        public void output(final BindingSet b,
                                           final StringBuilder sb) {
                            sb.append(esc(((URI) b.getValue("tweet1")).getLocalName()));
                            sb.append(", ");
                            long l1 = ((Literal) b.getValue("time1")).calendarValue().toGregorianCalendar().getTime().getTime();
                            sb.append(esc("" + l1));

                            sb.append(", ");

                            sb.append(esc(((URI) b.getValue("tweet2")).getLocalName()));
                            sb.append(", ");
                            long l2 = ((Literal) b.getValue("time2")).calendarValue().toGregorianCalendar().getTime().getTime();
                            sb.append(esc("" + l2));
                        }
                    });
                } finally {
                    os.close();
                }

                ////////////////////////////////////////////////////////////////

                query = "PREFIX dc: <http://purl.org/dc/terms/>\n" +
                        "PREFIX sioc: <http://rdfs.org/sioc/ns#>\n" +
                        "SELECT DISTINCT ?tweet ?account ?replyTo ?createdAt ?text WHERE {\n" +
                        "    ?tweet dc:created ?createdAt .\n" +
                        "    ?tweet sioc:content ?text .\n" +
                        "    ?tweet sioc:has_creator ?account .\n" +
                        // "    ?account sioc:id ?screenName .\n" +
                        "    OPTIONAL { ?tweet sioc:reply_of ?replyTo . } .\n" +
                        "}";

                os = new FileOutputStream("/tmp/tweets.csv");
                try {
                    doQuery(query, rc, new PrintStream(os), new CSVOutputter() {
                        public void output(final BindingSet b,
                                           final StringBuilder sb) {
                            sb.append(esc(((URI) b.getValue("tweet")).getLocalName()));
                            sb.append(", ");
                            sb.append(esc(((URI) b.getValue("account")).getLocalName()));
                            //  sb.append(esc(((Literal) b.getValue("screenName")).getLabel()));
                            sb.append(", ");
                            Value v = b.getValue("replyTo");
                            if (null == v) {
                                sb.append("\"\"");
                            } else {
                                sb.append(esc(((URI) v).getLocalName()));
                            }
                            sb.append(", ");
                            sb.append(esc(((Literal) b.getValue("createdAt")).getLabel()));
                            sb.append(", ");
                            sb.append(esc(((Literal) b.getValue("text")).getLabel()));
                        }
                    });
                } finally {
                    os.close();
                }

                ////////////////////////////////////////////////////////////////

                query = "PREFIX sioc: <http://rdfs.org/sioc/ns#>\n" +
                        "PREFIX sioct: <http://rdfs.org/sioc/types#>\n" +
                        "SELECT DISTINCT ?tweet ?topic WHERE {\n" +
                        "    ?tweet a sioct:MicroblogPost .\n" +
                        "    ?tweet sioc:topic ?topic .\n" +
                        "}";

                os = new FileOutputStream("/tmp/tweet_topics.csv");
                try {
                    doQuery(query, rc, new PrintStream(os), new CSVOutputter() {
                        public void output(final BindingSet b,
                                           final StringBuilder sb) {
                            sb.append(esc(((URI) b.getValue("tweet")).getLocalName()));
                            sb.append(", ");
                            sb.append(esc(((URI) b.getValue("topic")).getLocalName()));
                        }
                    });
                } finally {
                    os.close();
                }

                ////////////////////////////////////////////////////////////////

                query = "PREFIX sioc: <http://rdfs.org/sioc/ns#>\n" +
                        "PREFIX sioct: <http://rdfs.org/sioc/types#>\n" +
                        "SELECT DISTINCT ?tweet ?url WHERE {\n" +
                        "    ?tweet a sioct:MicroblogPost .\n" +
                        "    ?tweet sioc:links_to ?url .\n" +
                        "}";

                os = new FileOutputStream("/tmp/tweet_links.csv");
                try {
                    doQuery(query, rc, new PrintStream(os), new CSVOutputter() {
                        public void output(final BindingSet b,
                                           final StringBuilder sb) {
                            sb.append(esc(((URI) b.getValue("tweet")).getLocalName()));
                            sb.append(", ");
                            sb.append(esc(((URI) b.getValue("url")).stringValue()));
                        }
                    });
                } finally {
                    os.close();
                }

                ////////////////////////////////////////////////////////////////

            } finally {
                rc.rollback();
                rc.close();
            }
        } finally {
            sail.shutDown();
        }
    }

    private void csvPrint(final BindingSet b,
                          final String[] names) {
        String[] fields = new String[names.length];
        for (int i = 0; i < names.length; i++) {
            Value v = b.getValue(names[i]);
            fields[i] = (null == v) ? "" : v.toString();
        }

        csvPrint(fields);
    }

    private void csvPrint(final String[] fields) {
        PrintStream ps = System.out;

        boolean first = true;
        for (String f : fields) {
            if (first) {
                first = false;
            } else {
                ps.print(", ");
            }

            ps.print("\"");
            ps.print(escape(f));
            ps.print("\"");
        }
        ps.println("");
    }

    private String escape(final String s) {
        return s.replaceAll("\\\\", "\\\\")
                .replaceAll("\\n", " ")
                .replaceAll("\"", "\\\"");
    }

    private String esc(final String s) {
        return "\"" + escape(s) + "\"";
    }

    private interface CSVOutputter {
        void output(BindingSet b, StringBuilder sb);
    }

    private void doQuery(final String query,
                         final RepositoryConnection rc,
                         final PrintStream ps,
                         final CSVOutputter outputter) throws Exception {
        TupleQuery q = rc.prepareTupleQuery(QueryLanguage.SPARQL, query);
        TupleQueryResult r = q.evaluate();
        while (r.hasNext()) {
            StringBuilder sb = new StringBuilder();
            outputter.output(r.next(), sb);
            ps.println(sb.toString());
        }
    }
}
