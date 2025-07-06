package com.assaffin.games.functions.actions;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2WebSocketEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2WebSocketResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.amazonaws.services.lambda.runtime.LambdaLogger;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.TreeMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

public class ActionHandler implements RequestHandler<APIGatewayV2WebSocketEvent, APIGatewayV2WebSocketResponse> {
    protected static final ObjectMapper objectMapper = new ObjectMapper();
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
        context.getLogger().log("WebSocket event received: " + input);

        GameStatePayload payload = null;
        try {
            String bodyStr = input.getBody();
            payload = objectMapper.readValue(bodyStr, GameStatePayload.class);
        } catch (Exception e) {
            context.getLogger().log("Failed to parse body: " + e.getMessage());
        }

        String action = payload != null ? payload.getAction() : "";

        APIGatewayV2WebSocketResponse response = switch (action) {
            case "join" -> handleJoin(payload, context);
            case "create" -> handleCreate(payload, context);
            default -> handleDefault(payload, context);
        };

        APIGatewayV2WebSocketEvent.RequestContext rc = input.getRequestContext();
        String connectionId = rc.getConnectionId();
        String domain = rc.getDomainName();
        String stage = rc.getStage();
        
        // Send response back to the same connection
        if (!"localhost".equals(domain)) {
            try {
                sendToConnection(connectionId, domain, stage, response.getBody(), context);
                context.getLogger().log("Response sent successfully to connection: " + connectionId);
                return generateResponse("Message sent to WebSocket");
            } catch (Exception e) {
                context.getLogger().log("Failed to send response: " + e.getMessage());
                // Return the response anyway, even if WebSocket push failed
                return response;
            }
        } else {
            context.getLogger().log("Skipping API Gateway call for local testing. Response: " + response.getBody());
            // Return the actual response for local testing
            return response;
        }
    }

    private APIGatewayV2WebSocketResponse handleJoin(GameStatePayload payload, Context context) {
        String playerName = payload.getPlayerName() != null ? payload.getPlayerName() : "Unknown";
        context.getLogger().log("Handling join action for player: " + playerName);
        return generateResponse("Joined the game2: " + playerName);
    }

    private APIGatewayV2WebSocketResponse handleCreate(GameStatePayload payload, Context context) {
        context.getLogger().log("Handling create action");
        return generateResponse("Game created");
    }

    private APIGatewayV2WebSocketResponse handleDefault(GameStatePayload payload, Context context) {
        context.getLogger().log("Handling default action");
        return generateResponse("Default action response");
    }

    private APIGatewayV2WebSocketResponse generateResponse(String message) {
        APIGatewayV2WebSocketResponse response = new APIGatewayV2WebSocketResponse();
        response.setStatusCode(200);
        response.setBody(message);

        return response;
    }

    private void sendToConnection(String connectionId, String domain, String stage, String message, Context context) throws Exception {
        String endpoint = "https://" + domain + "/" + stage + "/@connections/" + connectionId;
        
        context.getLogger().log("Attempting to send message to endpoint: " + endpoint);
        context.getLogger().log("Message: " + message);
        
        // Get AWS credentials from environment
        String accessKey = System.getenv("AWS_ACCESS_KEY_ID");
        String secretKey = System.getenv("AWS_SECRET_ACCESS_KEY");
        String sessionToken = System.getenv("AWS_SESSION_TOKEN");
        String region = System.getenv("AWS_REGION");
        
        if (accessKey == null || secretKey == null || region == null) {
            throw new RuntimeException("AWS credentials not found in environment");
        }
        
        // Parse the endpoint to get service and host
        String service = "execute-api";
        String host = domain;
        
        // Create timestamp
        ZonedDateTime now = ZonedDateTime.now(java.time.ZoneOffset.UTC);
        String timestamp = now.format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"));
        String dateStamp = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        
        // Create canonical request
        String httpMethod = "POST";
        String canonicalUri = "/" + stage + "/@connections/" + connectionId;
        String canonicalQueryString = "";
        
        // Headers
        TreeMap<String, String> headers = new TreeMap<>();
        headers.put("content-type", "application/json");
        headers.put("host", host);
        headers.put("x-amz-date", timestamp);
        if (sessionToken != null) {
            headers.put("x-amz-security-token", sessionToken);
        }
        
        // Create canonical headers string
        StringBuilder canonicalHeaders = new StringBuilder();
        for (var entry : headers.entrySet()) {
            canonicalHeaders.append(entry.getKey()).append(":").append(entry.getValue()).append("\n");
        }
        
        // Create signed headers string
        StringBuilder signedHeaders = new StringBuilder();
        for (String headerName : headers.keySet()) {
            if (signedHeaders.length() > 0) signedHeaders.append(";");
            signedHeaders.append(headerName);
        }
        
        // Create payload hash
        String payloadHash = sha256Hex(message);
        
        // Create canonical request
        String canonicalRequest = httpMethod + "\n" +
                canonicalUri + "\n" +
                canonicalQueryString + "\n" +
                canonicalHeaders.toString() + "\n" +
                signedHeaders.toString() + "\n" +
                payloadHash;
        
        // Create string to sign
        String algorithm = "AWS4-HMAC-SHA256";
        String credentialScope = dateStamp + "/" + region + "/" + service + "/aws4_request";
        String stringToSign = algorithm + "\n" +
                timestamp + "\n" +
                credentialScope + "\n" +
                sha256Hex(canonicalRequest);
        
        // Calculate signature
        String signature = getSignatureKey(secretKey, dateStamp, region, service, stringToSign);
        
        // Create authorization header
        String authorizationHeader = algorithm + " " +
                "Credential=" + accessKey + "/" + credentialScope + ", " +
                "SignedHeaders=" + signedHeaders.toString() + ", " +
                "Signature=" + signature;
        
        // Create request with proper headers
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Content-Type", "application/json")
                .header("Host", host)
                .header("X-Amz-Date", timestamp)
                .header("Authorization", authorizationHeader)
                .POST(HttpRequest.BodyPublishers.ofString(message));
        
        if (sessionToken != null) {
            requestBuilder.header("X-Amz-Security-Token", sessionToken);
        }
        
        HttpRequest request = requestBuilder.build();
        
        // Send the request with proper resource management
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            
            context.getLogger().log("Response status: " + response.statusCode());
            context.getLogger().log("Response body: " + response.body());
            
            if (response.statusCode() != 200) {
                throw new RuntimeException("Failed to send message. Response code: " + response.statusCode() + ", Body: " + response.body());
            }
        }
    }
    
    private static String sha256Hex(String input) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }
    
    private static String getSignatureKey(String key, String dateStamp, String regionName, String serviceName, String stringToSign) throws Exception {
        byte[] kDate = hmacSha256(("AWS4" + key).getBytes(StandardCharsets.UTF_8), dateStamp);
        byte[] kRegion = hmacSha256(kDate, regionName);
        byte[] kService = hmacSha256(kRegion, serviceName);
        byte[] kSigning = hmacSha256(kService, "aws4_request");
        return hmacSha256Hex(kSigning, stringToSign);
    }
    
    private static byte[] hmacSha256(byte[] key, String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKeySpec = new SecretKeySpec(key, "HmacSHA256");
        mac.init(secretKeySpec);
        return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    }
    
    private static String hmacSha256Hex(byte[] key, String data) throws Exception {
        byte[] hash = hmacSha256(key, data);
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
