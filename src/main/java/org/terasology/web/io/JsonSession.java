/*
 * Copyright 2017 MovingBlocks
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.terasology.web.io;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonSyntaxException;
import org.terasology.config.Config;
import org.terasology.identity.storageServiceClient.BigIntegerBase64Serializer;
import org.terasology.web.EngineRunner;
import org.terasology.web.authentication.AuthenticationFailedException;
import org.terasology.web.authentication.AuthenticationHandshakeHandler;
import org.terasology.web.authentication.AuthenticationHandshakeHandlerImpl;
import org.terasology.web.authentication.ClientAuthenticationMessage;
import org.terasology.web.authentication.HandshakeHello;

import java.math.BigInteger;

public class JsonSession {

    private static final Gson GSON = new GsonBuilder()
            .registerTypeHierarchyAdapter(BigInteger.class, BigIntegerBase64Serializer.getInstance())
            .registerTypeAdapter(byte[].class, ByteArrayBase64Serializer.getInstance())
            .create();

    private AuthenticationHandshakeHandler authHandler;
    private String clientId; //this is the same UUID used to identify players in the engine, and is only set when the client is correctly authenticated

    JsonSession(AuthenticationHandshakeHandler authHandler) {
        this.authHandler = authHandler;
    }

    public JsonSession() {
        this(new AuthenticationHandshakeHandlerImpl(EngineRunner.getContext().get(Config.class).getSecurity().getServerPublicCertificate()));
    }

    public boolean isAuthenticated() {
        return clientId != null;
    }

    public ActionResult initAuthentication() {
        if (isAuthenticated()) {
            return new ActionResult(ActionResult.Status.UNAUTHORIZED, "Already authenticated");
        }
        HandshakeHello serverHello = authHandler.initServerHello();
        return new ActionResult(GSON.toJsonTree(serverHello));
    }

    public ActionResult finishAuthentication(JsonElement clientMessage) {
        if (isAuthenticated()) {
            return new ActionResult(ActionResult.Status.UNAUTHORIZED, "Already authenticated");
        }
        try {
            ClientAuthenticationMessage clientAuthentication = GSON.fromJson(clientMessage, ClientAuthenticationMessage.class);
            authHandler.authenticate(clientAuthentication.getClientHello(), clientAuthentication.getSignature());
            clientId = clientAuthentication.getClientHello().getCertificate().getId();
            return ActionResult.OK;
        } catch (NullPointerException | JsonSyntaxException ex) {
            return new ActionResult(ActionResult.Status.BAD_REQUEST);
        } catch (AuthenticationFailedException ex) {
            return new ActionResult(ActionResult.Status.UNAUTHORIZED);
        }
    }

}
