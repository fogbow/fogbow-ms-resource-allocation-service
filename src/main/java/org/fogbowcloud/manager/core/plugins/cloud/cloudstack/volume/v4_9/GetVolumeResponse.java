package org.fogbowcloud.manager.core.plugins.cloud.cloudstack.volume.v4_9;

import static org.fogbowcloud.manager.core.plugins.cloud.cloudstack.CloudStackRestApiConstants.Volume.*;

import java.util.List;
import org.fogbowcloud.manager.util.GsonHolder;
import com.google.gson.annotations.SerializedName;

/**
 * Documentation: https://cloudstack.apache.org/api/apidocs-4.9/apis/listVolumes.html
 *
 * Response example:
 * {
 *      "listvolumesresponse": {
 *          "volume": [{
 *              "id": "dad76621-edcd-4968-a152-74d877d1961b",
 *              "name": "ca43bccc-21a6-4f88-8fac-c88ea386a451",
 *              "size": 1073741824,
 *              "state": "Ready",
 *          }]
 *      }
 * }
 *
 * We use the @SerializedName annotation to specify that the request parameter is not equal to the class field.
 */
public class GetVolumeResponse {

    @SerializedName(VOLUMES_KEY_JSON)
    private ListVolumesResponse response;
    
    public class ListVolumesResponse {
        
        @SerializedName(VOLUME_KEY_JSON)
        private List<Volume> volumes;
        
    }
    
    public List<Volume> getVolumes() {
        return this.response.volumes;
    }
    
    public static GetVolumeResponse fromJson(String json) {
        return GsonHolder.getInstance().fromJson(json, GetVolumeResponse.class);
    }
    
    public class Volume {
        
        @SerializedName(ID_KEY_JSON)
        private String id;
        
        @SerializedName(NAME_KEY_JSON)
        private String name;
        
        @SerializedName(SIZE_KEY_JSON)
        private int size;
        
        @SerializedName(STATE_KEY_JSON)
        private String state;

        public String getId() {
            return this.id;
        }

        public String getName() {
            return this.name;
        }

        public int getSize() {
            return this.size;
        }

        public String getState() {
            return this.state;
        }
        
    }
    
}
