package com.vizcom.symphony.dal.communicator.vizcom.place;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public class PlaceAuthToken {
    private String access_token;
    private long created_at;
    private long expires_in;
    private String refresh_token;
    private String scope;
    private String token_type;

    private final JSONParser JSON = new JSONParser();
    /**
     * { "access_token":
     * "xxx",
     * "created_at": 1559193360, "expires_in": 1209600, "refresh_token":
     * "xxx", "scope":
     * "public", "token_type": "bearer" }
     */
    public PlaceAuthToken(String tokenResponse) throws ParseException {
        JSONObject jsonObject = (JSONObject) JSON.parse(tokenResponse);
        this.access_token = (String) jsonObject.get("access_token");
        this.created_at = (long) jsonObject.get("created_at");
        this.expires_in = (long) jsonObject.get("expires_in");
        this.refresh_token = (String) jsonObject.get("refresh_token");
        this.scope = (String) jsonObject.get("scope");
        this.token_type = (String) jsonObject.get("token_type");
    }

    public boolean isValid() {
        return this.created_at + this.expires_in >= (System.currentTimeMillis() / 1000);
    }

    /**
     * @return the access_token
     */
    public String getAccessToken() {
        return access_token;
    }

    /**
     * @return the created_at
     */
    public long getCreatedAt() {
        return created_at;
    }

    /**
     * @return the expires_in
     */
    public long getExpiresIn() {
        return expires_in;
    }

    /**
     * @return the refresh_token
     */
    public String getRefreshToken() {
        return refresh_token;
    }

    /**
     * @return the scope
     */
    public String getScope() {
        return scope;
    }

    /**
     * @return the token_type
     */
    public String getTokenType() {
        return token_type;
    }
}