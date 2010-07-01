package net.fortytwo.twitlogic.vocabs;

/**
 * User: josh
 * Date: Jun 15, 2010
 * Time: 4:00:41 PM
 */
public interface DBpediaResource {
    public static final String BASE_URI = "http://dbpedia.org/resource/";

    public static final String
            ADMINISTRATIVE_DIVISION = BASE_URI + "Administrative_division",
            CITY = BASE_URI + "City",
            COUNTRY = BASE_URI + "Country",
            NEIGHBORHOOD = BASE_URI + "Neighbourhood";  // Note: spelled with 'ou'
}