package com.assaffin.games.functions.actions;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.api.client.AWSLambda;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2WebSocketEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2WebSocketResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.apigatewaymanagementapi.ApiGatewayManagementApiClient;
import software.amazon.awssdk.services.apigatewaymanagementapi.model.GoneException;
import software.amazon.awssdk.services.apigatewaymanagementapi.model.PostToConnectionRequest;

import java.net.URI;

public class ActionHandler implements RequestHandler<APIGatewayV2WebSocketEvent, Object> {
    protected static final ObjectMapper objectMapper = new ObjectMapper();

    public static void main(String[] args) throws Throwable {
        args = new String[]{"com.assaffin.games.functions.actions.ActionHandler::handleRequest"};
        AWSLambda.main(args);
    }

    @Override
    public APIGatewayV2WebSocketResponse handleRequest(APIGatewayV2WebSocketEvent input, Context context) {
        context.getLogger().log("WebSocket event received: " + input);

        GameStatePayload payload = null;
        try {
            String bodyStr = input.getBody();
            payload = objectMapper.readValue(bodyStr, GameStatePayload.class);
        } catch (Exception e) {
            context.getLogger().log("Failed to parse body: " + e.getMessage());
        }

        String action = payload != null ? payload.getAction() : "";

        Object response = switch (action) {
            case "join" -> handleJoin(payload, context);
            case "create" -> handleCreate(payload, context);
            default -> handleDefault(payload, context);
        };

        APIGatewayV2WebSocketEvent.RequestContext rc = input.getRequestContext();
        String connectionId = rc.getConnectionId();
        String domain = rc.getDomainName();
        String stage = rc.getStage();
        String endpoint = "https://" + domain + "/" + stage;

        ApiGatewayManagementApiClient client = ApiGatewayManagementApiClient.builder()
                .endpointOverride(URI.create(endpoint))
                .build();

        try {
            client.postToConnection(PostToConnectionRequest.builder()
                    .connectionId(connectionId)
                    .data(SdkBytes.fromUtf8String(response.toString()))
                    .build());
        } catch (GoneException e) {
            context.getLogger().log("Connection gone: " + connectionId);
        } catch (Exception e) {
            context.getLogger().log("Send failed: " + e.getMessage());
        }
        return generateResponse("OK?!");
    }

    private Object handleJoin(GameStatePayload payload, Context context) {
        String playerName = payload.getPlayerName() != null ? payload.getPlayerName() : "Unknown";
        context.getLogger().log("Handling join action for player: " + playerName);
        return generateResponse("Joined the game: " + playerName);
    }

    private Object handleCreate(GameStatePayload payload, Context context) {
        context.getLogger().log("Handling create action");
        return generateResponse("Game created");
    }

    private Object handleDefault(GameStatePayload payload, Context context) {
        context.getLogger().log("Handling default action");
        return generateResponse("Default action response");
    }

    private APIGatewayV2WebSocketResponse generateResponse(String message) {
        APIGatewayV2WebSocketResponse response = new APIGatewayV2WebSocketResponse();
        response.setStatusCode(200);
        response.setBody(message);

        return response;
    }
}
