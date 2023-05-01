package org.incenp.obofoundry.helpers;

import org.semanticweb.owlapi.model.IRI;

/**
 * Helper class to deal with short IRIs ("CURIEs").
 */
public class CurieManager {

    private static CurieManager instance = new CurieManager();

    public static CurieManager getInstance() {
        return instance;
    }

    public IRI expand(String curie) throws IllegalArgumentException {
        if ( curie == null || curie.length() == 0 ) {
            throw new IllegalArgumentException("Null or zero-length CURIE");
        }

        if ( curie.startsWith("http:") ) {
            return IRI.create(curie);
        }

        String[] parts = curie.split(":", 2);
        if ( parts.length == 1 ) {
            return IRI.create(curie);
        }

        if ( parts[0].length() == 0 || parts[1].length() == 0 ) {
            throw new IllegalArgumentException("Zero-length CURIE part");
        }

        return IRI.create(String.format("http://purl.obolibrary.org/obo/%s_%s", parts[0], parts[1]));
    }
}
