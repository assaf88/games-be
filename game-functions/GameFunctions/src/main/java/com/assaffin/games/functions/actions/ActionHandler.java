package com.assaffin.games.functions.actions;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2WebSocketEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2WebSocketResponse;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.util.logging.Logger;
import software.amazon.awssdk.services.apigatewaymanagementapi.ApiGatewayManagementApiClient;
import software.amazon.awssdk.services.apigatewaymanagementapi.model.PostToConnectionRequest;
import software.amazon.awssdk.core.SdkBytes;


public class ActionHandler implements RequestHandler<APIGatewayV2WebSocketEvent, APIGatewayV2WebSocketResponse> {
    protected static final ObjectMapper objectMapper = new ObjectMapper();
    private static final Logger logger = Logger.getLogger(ActionHandler.class.getName());
    private static final String RUNTIME_API = System.getenv("AWS_LAMBDA_RUNTIME_API");
    private static final String RUNTIME_BASE_URL = "http://" + RUNTIME_API + "/2018-06-01/runtime";

    public static void main(String[] args) {
        ActionHandler handler = new ActionHandler();
        try (HttpClient httpClient = HttpClient.newHttpClient()) {

            while (true) {
                try {
                    // Get next invocation
                    HttpRequest nextRequest = HttpRequest.newBuilder()
                            .uri(URI.create(RUNTIME_BASE_URL + "/invocation/next"))
                            .GET()
                            .build();

                    HttpResponse<String> nextResponse = httpClient.send(nextRequest, HttpResponse.BodyHandlers.ofString());

                    if (nextResponse.statusCode() != 200) {
                        throw new RuntimeException("Failed to get next invocation: " + nextResponse.statusCode());
                    }

                    String requestId = nextResponse.headers().firstValue("lambda-runtime-aws-request-id").orElse("");
                    String traceId = nextResponse.headers().firstValue("lambda-runtime-trace-id").orElse("");

                    // Parse the event
                    APIGatewayV2WebSocketEvent event = objectMapper.readValue(nextResponse.body(), APIGatewayV2WebSocketEvent.class);

                    // Create a simple context
                    Context context = new Context() {
                        @Override
                        public String getAwsRequestId() {
                            return requestId;
                        }

                        @Override
                        public String getLogGroupName() {
                            return "";
                        }

                        @Override
                        public String getLogStreamName() {
                            return "";
                        }

                        @Override
                        public String getFunctionName() {
                            return "";
                        }

                        @Override
                        public String getFunctionVersion() {
                            return "";
                        }

                        @Override
                        public String getInvokedFunctionArn() {
                            return "";
                        }

                        @Override
                        public com.amazonaws.services.lambda.runtime.CognitoIdentity getIdentity() {
                            return null;
                        }

                        @Override
                        public com.amazonaws.services.lambda.runtime.ClientContext getClientContext() {
                            return null;
                        }

                        @Override
                        public int getRemainingTimeInMillis() {
                            return 30000;
                        }

                        @Override
                        public int getMemoryLimitInMB() {
                            return 512;
                        }

                        @Override
                        public LambdaLogger getLogger() {
                            return new LambdaLogger() {
                                @Override
                                public void log(String message) {
                                    System.out.println(message);
                                }

                                @Override
                                public void log(byte[] message) {
                                    System.out.println(new String(message));
                                }
                            };
                        }
                    };

                    // Handle the request
                    APIGatewayV2WebSocketResponse response = handler.handleRequest(event, context);

                    // Send the response
                    String responseJson = objectMapper.writeValueAsString(response);
                    HttpRequest responseRequest = HttpRequest.newBuilder()
                            .uri(URI.create(RUNTIME_BASE_URL + "/invocation/" + requestId + "/response"))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(responseJson))
                            .build();

                    HttpResponse<String> responseResponse = httpClient.send(responseRequest, HttpResponse.BodyHandlers.ofString());

                    if (responseResponse.statusCode() != 202) {
                        throw new RuntimeException("Failed to send response: " + responseResponse.statusCode());
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                    System.exit(1);
                }
            }
        }
    }

    @Override
    public APIGatewayV2WebSocketResponse handleRequest(APIGatewayV2WebSocketEvent input, Context context) {
        //logger.info("WebSocket event received: " + input);

        GameStatePayload payload = null;
        try {
            String bodyStr = input.getBody();
            payload = objectMapper.readValue(bodyStr, GameStatePayload.class);
        } catch (Exception e) {
            logger.warning("Failed to parse body: " + e.getMessage());
        }

        String action = payload != null ? payload.getAction() : "";

        APIGatewayV2WebSocketResponse response = switch (action) {
            case "join" -> handleJoin(payload);
            case "create" -> handleCreate(payload);
            default -> handleDefault(payload);
        };

        APIGatewayV2WebSocketEvent.RequestContext rc = input.getRequestContext();
        String connectionId = rc.getConnectionId();
        String domain = rc.getDomainName();
        String stage = rc.getStage();
        
        // Send response back to the same connection
        if (!"localhost".equals(domain)) {
            try {
                sendToConnection(connectionId, domain, stage, response.getBody());
                logger.info("Response sent successfully to connection: " + connectionId);
                return generateResponse("Message sent to WebSocket");
        } catch (Exception e) {
                logger.warning("Failed to send response: " + e.getMessage());
                // Return the response anyway, even if WebSocket push failed
                return response;
            }
        } else {
            //logger.info("Skipping API Gateway call for local testing. Response: " + response.getBody());
            // Return the actual response for local testing
            return response;
        }
    }

    private APIGatewayV2WebSocketResponse handleJoin(GameStatePayload payload) {
        String playerName = payload.getPlayerName() != null ? payload.getPlayerName() : "Unknown";
        logger.info("Handling join action for player: " + playerName);
        return generateResponse("Joined the game4: " + playerName);
    }

    private APIGatewayV2WebSocketResponse handleCreate(GameStatePayload payload) {
        logger.info("Handling create action");
        return generateResponse("Game created");
    }

    private APIGatewayV2WebSocketResponse handleDefault(GameStatePayload payload) {
        logger.info("Handling default action");
        return generateResponse("Default action response");
    }

    private APIGatewayV2WebSocketResponse generateResponse(String message) {
        APIGatewayV2WebSocketResponse response = new APIGatewayV2WebSocketResponse();
        response.setStatusCode(200);
        response.setBody(message);

        return response;
    }

    private void sendToConnection(String connectionId, String domain, String stage, String message) throws Exception {
        String endpoint = "https://" + domain + "/" + stage;
        
        logger.info("Sending message to WebSocket connection: " + connectionId);
        logger.info("Message: " + message);
        logger.info("Endpoint: " + endpoint);
        
        // Use AWS SDK v2 for API Gateway Management API
        try (ApiGatewayManagementApiClient client = ApiGatewayManagementApiClient.builder()
                .endpointOverride(URI.create(endpoint))
                .build()) {
            
            PostToConnectionRequest request = PostToConnectionRequest.builder()
                    .connectionId(connectionId)
                    .data(SdkBytes.fromUtf8String(message))
                    .build();
            
            client.postToConnection(request);
            
            logger.info("WebSocket message sent successfully to connection: " + connectionId);
        } catch (Exception e) {
            logger.warning("Error sending WebSocket message: " + e.getMessage());
            throw e;
        }
    }
}
