package com.assaffin.games.wsserver;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;
import software.amazon.awssdk.services.apigatewaymanagementapi.ApiGatewayManagementApiClient;
import software.amazon.awssdk.services.apigatewaymanagementapi.model.*;
import software.amazon.awssdk.core.SdkBytes;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class WebSocketHandler {

    private final DynamoDbClient dynamoDbClient;
    private final ApiGatewayManagementApiClient apiClient;
    private final String connectionTableName = "WebSocketConnections"; // DynamoDB Table for storing connections
    private final String apiGatewayEndpoint = "https://your-api-id.execute-api.region.amazonaws.com"; // WebSocket API Gateway endpoint

    public WebSocketHandler() {
        this.dynamoDbClient = DynamoDbClient.create();
        this.apiClient = ApiGatewayManagementApiClient.builder()
                .endpointOverride(URI.create(apiGatewayEndpoint))
                .build();
    }

    // Add connectionId when a client connects
    public void addConnection(String connectionId) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("ConnectionId", AttributeValue.builder().s(connectionId).build());
        item.put("Timestamp", AttributeValue.builder().s(String.valueOf(System.currentTimeMillis())).build());

        PutItemRequest putItemRequest = PutItemRequest.builder()
                .tableName(connectionTableName)
                .item(item)
                .build();
        dynamoDbClient.putItem(putItemRequest);
    }

    // Remove connectionId when a client disconnects
    public void removeConnection(String connectionId) {
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("ConnectionId", AttributeValue.builder().s(connectionId).build());

        DeleteItemRequest deleteItemRequest = DeleteItemRequest.builder()
                .tableName(connectionTableName)
                .key(key)
                .build();
        dynamoDbClient.deleteItem(deleteItemRequest);
    }

    // Fetch all connection IDs from DynamoDB
    public List<String> getAllConnections() {
        ScanRequest scanRequest = ScanRequest.builder().tableName(connectionTableName).build();
        ScanResponse scanResponse = dynamoDbClient.scan(scanRequest);

        // Extract connection IDs
        return scanResponse.items().stream()
                .map(item -> item.get("ConnectionId").s())
                .collect(Collectors.toList());
    }

    // Broadcast message to all connections
    public void broadcastMessage(String message) {
        // Get all connection IDs
        List<String> connectionIds = getAllConnections();

        // Send the message to all connected clients
        for (String connectionId : connectionIds) {
            sendMessageToClient(connectionId, message);
        }
    }

    // Send a message to a single client
    private void sendMessageToClient(String connectionId, String message) {
        try {
            PostToConnectionRequest postToConnectionRequest = PostToConnectionRequest.builder()
                    .connectionId(connectionId)
                    .data(SdkBytes.fromUtf8String(message))
                    .build();

            PostToConnectionResponse response = apiClient.postToConnection(postToConnectionRequest);
            System.out.println("Message sent to " + connectionId + ": " + response.sdkHttpResponse().statusCode());
        } catch (GoneException e) {
            System.out.println("Connection " + connectionId + " is gone. Removing.");
            removeConnection(connectionId); // Clean up disconnected clients
        } catch (Exception e) {
            System.err.println("Failed to send message to " + connectionId + ": " + e.getMessage());
        }
    }
}
