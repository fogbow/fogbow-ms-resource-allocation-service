package org.fogbowcloud.manager.core.models;

import java.util.HashMap;
import java.util.Map;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;

public class StatusResponseMap {

    private String responseString;
    private HttpResponse httpResponse;
    private Map<Integer, ErrorResponse> statusResponseMap;

    public StatusResponseMap(HttpResponse httpResponse, String responseString) {
        this.responseString = responseString;
        this.httpResponse = httpResponse;
        this.statusResponseMap = new HashMap<>();

        fillStatusResponseMap();
    }

    private void fillStatusResponseMap() {
        statusResponseMap.put(
                HttpStatus.SC_UNAUTHORIZED,
                new ErrorResponse(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED));
        statusResponseMap.put(
                HttpStatus.SC_NOT_FOUND,
                new ErrorResponse(ErrorType.NOT_FOUND, ResponseConstants.NOT_FOUND));
        statusResponseMap.put(
                HttpStatus.SC_BAD_REQUEST,
                new ErrorResponse(ErrorType.BAD_REQUEST, responseString));

        if (responseString.contains(ResponseConstants.QUOTA_EXCEEDED_FOR_INSTANCES)) {
            statusResponseMap.put(
                    HttpStatus.SC_REQUEST_TOO_LONG,
                    new ErrorResponse(
                            ErrorType.QUOTA_EXCEEDED,
                            ResponseConstants.QUOTA_EXCEEDED_FOR_INSTANCES));
            statusResponseMap.put(
                    HttpStatus.SC_FORBIDDEN,
                    new ErrorResponse(
                            ErrorType.QUOTA_EXCEEDED,
                            ResponseConstants.QUOTA_EXCEEDED_FOR_INSTANCES));
        } else {
            statusResponseMap.put(
                    HttpStatus.SC_REQUEST_TOO_LONG,
                    new ErrorResponse(ErrorType.BAD_REQUEST, responseString));
            statusResponseMap.put(
                    HttpStatus.SC_FORBIDDEN,
                    new ErrorResponse(ErrorType.BAD_REQUEST, responseString));
        }

        if (responseString.contains(ResponseConstants.NO_VALID_HOST_FOUND)) {
            statusResponseMap.put(
                    HttpStatus.SC_INTERNAL_SERVER_ERROR,
                    new ErrorResponse(
                            ErrorType.NO_VALID_HOST_FOUND, ResponseConstants.NO_VALID_HOST_FOUND));
        }
    }

    public ErrorResponse getStatusResponse(Integer key) {
        if (key > 204) {
            return new ErrorResponse(
                    ErrorType.BAD_REQUEST,
                    "Status code: "
                            + httpResponse.getStatusLine().toString()
                            + " | Message:"
                            + httpResponse);
        } else {
            return statusResponseMap.get(key);
        }
    }
}
