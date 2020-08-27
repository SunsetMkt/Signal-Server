package org.whispersystems.textsecuregcm.controllers;

import com.codahale.metrics.annotation.Timed;
import com.google.common.annotations.VisibleForTesting;
import org.whispersystems.textsecuregcm.storage.FeatureFlagsManager;

import javax.ws.rs.DELETE;
import javax.ws.rs.FormParam;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.stream.Collectors;

@Path("/v1/featureflag")
public class FeatureFlagsController {

    private final FeatureFlagsManager featureFlagsManager;
    private final List<byte[]>        authorizedTokens;

    public FeatureFlagsController(final FeatureFlagsManager featureFlagsManager, final List<String> authorizedTokens) {
        this.featureFlagsManager = featureFlagsManager;
        this.authorizedTokens    = authorizedTokens.stream().map(token -> token.getBytes(StandardCharsets.UTF_8)).collect(Collectors.toList());
    }

    @Timed
    @PUT
    @Path("/{featureFlag}")
    public void set(@HeaderParam("Token") final String token, @PathParam("featureFlag") final String featureFlag, @FormParam("active") final boolean active) {
        if (!isAuthorized(token)) {
            throw new WebApplicationException(Response.Status.UNAUTHORIZED);
        }

        featureFlagsManager.setFeatureFlag(featureFlag, active);
    }

    @Timed
    @DELETE
    @Path("/{featureFlag}")
    public void delete(@HeaderParam("Token") final String token, @PathParam("featureFlag") final String featureFlag) {
        if (!isAuthorized(token)) {
            throw new WebApplicationException(Response.Status.UNAUTHORIZED);
        }

        featureFlagsManager.deleteFeatureFlag(featureFlag);
    }

    @VisibleForTesting
    boolean isAuthorized(final String token) {
        if (token == null) {
            return false;
        }

        final byte[] tokenBytes = token.getBytes(StandardCharsets.UTF_8);

        boolean authorized = false;

        for (final byte[] authorizedToken : authorizedTokens) {
            //noinspection IfStatementMissingBreakInLoop
            if (MessageDigest.isEqual(authorizedToken, tokenBytes)) {
                authorized = true;
            }
        }
        
        return authorized;
    }
}
