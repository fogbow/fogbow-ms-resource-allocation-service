package org.fogbowcloud.manager.api.http;

import java.util.HashMap;

import org.fogbowcloud.manager.core.intercomponent.exceptions.RemoteRequestException;
import org.fogbowcloud.manager.core.ApplicationFacade;
import org.fogbowcloud.manager.core.exceptions.PropertyNotSpecifiedException;
import org.fogbowcloud.manager.core.exceptions.UnauthenticatedException;
import org.fogbowcloud.manager.core.models.images.Image;
import org.fogbowcloud.manager.core.plugins.exceptions.TokenCreationException;
import org.fogbowcloud.manager.core.plugins.exceptions.UnauthorizedException;
import org.apache.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = ImageRequestController.IMAGE_ENDPOINT)
public class ImageRequestController {

    public static final String IMAGE_ENDPOINT = "images";

    private final String FEDERATION_TOKEN_VALUE_HEADER_KEY = "federationTokenValue";

    private final String MEMBER_ID_HEADER_KEY = "memberId";


    private final Logger LOGGER = Logger.getLogger(ImageRequestController.class);

    @RequestMapping(method = RequestMethod.GET)
    public ResponseEntity<HashMap<String, String>> getAllImages(
            @RequestHeader(value = FEDERATION_TOKEN_VALUE_HEADER_KEY) String federationTokenValue,
            @RequestHeader(value = MEMBER_ID_HEADER_KEY) String memberId)
            throws UnauthenticatedException, UnauthorizedException, PropertyNotSpecifiedException,
            TokenCreationException, RemoteRequestException {
        HashMap<String, String> imagesMap = ApplicationFacade.getInstance().getAllImages(memberId, federationTokenValue);
        return new ResponseEntity<>(imagesMap, HttpStatus.OK);
    }

    @RequestMapping(value = "/{id}", method = RequestMethod.GET)
    public ResponseEntity<Image> getImage(
            @PathVariable String imageId,
            @RequestHeader(value = FEDERATION_TOKEN_VALUE_HEADER_KEY) String federationTokenValue,
            @RequestHeader(value = MEMBER_ID_HEADER_KEY) String memberId)
            throws UnauthenticatedException, UnauthorizedException, RemoteRequestException, TokenCreationException,
            PropertyNotSpecifiedException {
        Image image = ApplicationFacade.getInstance().getImage(memberId, imageId, federationTokenValue);
        return new ResponseEntity<>(image, HttpStatus.OK);
    }
}
