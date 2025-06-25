package com.assaffin.games.functions.actions;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
//import com.fasterxml.jackson.databind.JsonNode;
//import com.fasterxml.jackson.databind.ObjectMapper;

import io.micronaut.serde.ObjectMapper;
import io.micronaut.context.ApplicationContext;

import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.apigatewaymanagementapi.ApiGatewayManagementApiClient;
import software.amazon.awssdk.services.apigatewaymanagementapi.model.GoneException;
import software.amazon.awssdk.services.apigatewaymanagementapi.model.PostToConnectionRequest;

import java.net.URI;
import java.util.Map;

public class ActionHandler implements RequestHandler<Map<String, Object>, Object> {

    private static final ApplicationContext ctx = ApplicationContext.run();
    private static final ObjectMapper objectMapper = ctx.getBean(ObjectMapper.class);


    @Override
    public Object handleRequest(Map<String, Object> input, Context context) {
        context.getLogger().log("WebSocket event received: " + input);

        GameStatePayload payload = null;
        if (input.containsKey("body")) {
            try {
                String bodyStr = (String) input.get("body");
                payload = objectMapper.readValue(bodyStr, GameStatePayload.class);
            } catch (Exception e) {
                context.getLogger().log("Failed to parse body: " + e.getMessage());
            }
        }

        String action = payload != null ? payload.action : "";

        Object response = switch (action) {
            case "join" -> handleJoin(payload, context);
            case "create" -> handleCreate(payload, context);
            default -> handleDefault(payload, context);
        };

        if (isApiGatewayWebSocket(input)) {
            Map<String, Object> rc = (Map<String, Object>) input.get("requestContext");
            String connectionId = (String) rc.get("connectionId");
            String domain = (String) rc.get("domainName");
            String stage = (String) rc.get("stage");
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
            return generateResponse("OK");
        } else {
            return response;
        }
    }

    private boolean isApiGatewayWebSocket(Map<String, Object> input) {
        Object rc = input.get("requestContext");
        return (rc instanceof Map<?, ?> rcMap) && rcMap.containsKey("connectionId");
    }

    private Object handleJoin(GameStatePayload payload, Context context) {
        String playerName = payload.playerName != null ? payload.playerName : "Unknown";
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

    private Map<String, String> generateResponse(String message) {
        return Map.of("statusCode", "200", "body", message);
    }
}


//public class ActionHandler implements RequestHandler<Map<String, Object>, Object> {
//
//    private static final ObjectMapper objectMapper = new ObjectMapper();
//
//    @Override
//    public Object handleRequest(Map<String, Object> input, Context context) {
//        context.getLogger().log("WebSocket event received: " + input);
//
//        JsonNode bodyJson = null;
//        if (input.containsKey("body")) {
//            try {
//                bodyJson = objectMapper.readTree((String) input.get("body"));
//            } catch (Exception e) {
//                context.getLogger().log("Failed to parse JSON body: " + e.getMessage());
//            }
//        }
//
//        String action = (bodyJson != null && bodyJson.has("action")) ? bodyJson.get("action").asText() : "";
//
//        Object response = switch (action) {
//            case "join" -> handleJoin(bodyJson, context);
//            case "create" -> handleCreate(bodyJson, context);
//            default -> handleDefault(bodyJson, context);
//        };
//
//        if (isApiGatewayWebSocket(input)) {
//            Map<String, Object> rc = (Map<String, Object>) input.get("requestContext");
//            String connectionId = (String) rc.get("connectionId");
//            String domain = (String) rc.get("domainName");
//            String stage = (String) rc.get("stage");
//
//            String endpoint = "https://" + domain + "/" + stage;
//
//            ApiGatewayManagementApiClient client = ApiGatewayManagementApiClient.builder()
//                    .endpointOverride(URI.create(endpoint))
//                    .build();
//
//            try {
//                client.postToConnection(PostToConnectionRequest.builder()
//                        .connectionId(connectionId)
//                        .data(SdkBytes.fromUtf8String(response.toString()))
//                        .build());
//            } catch (GoneException e) {
//                context.getLogger().log("Connection gone: " + connectionId);
//            } catch (Exception e) {
//                context.getLogger().log("Send failed: " + e.getMessage());
//            }
//            return generateResponse("OK");
//        } else {
//            return response; // for REST/local testing
//        }
//    }
//
//    //continue from the local websocket server
//
//    private boolean isApiGatewayWebSocket(Map<String, Object> input) {
//        Object rc = input.get("requestContext");
//        if (!(rc instanceof Map)) return false;
//        return ((Map<?, ?>) rc).containsKey("connectionId");
//    }
//
//    private Object handleJoin(JsonNode bodyJson, Context context) {
//        String playerName = bodyJson != null && bodyJson.has("playerName") ? bodyJson.get("playerName").asText() : "Unknown";
//        context.getLogger().log("Handling join action for player: " + playerName);
//        // Your join logic here
//        return generateResponse("Joined the game: " + playerName);
//    }
//
//    private Object handleCreate(JsonNode bodyJson, Context context) {
//        context.getLogger().log("Handling create action");
//        // Your create logic here, e.g., read playerName if needed
//        return generateResponse("Game created");
//    }
//
//    private Object handleDefault(JsonNode bodyJson, Context context) {
//        context.getLogger().log("Handling default action");
//        return generateResponse("Default action response");
//    }
//
//    private Map<String, String> generateResponse(String message) {
//        return Map.of(
//                "statusCode", "200",
//                "body", message
//        );
//    }
//}
