package com.assaffin.games.wsserver;

import com.amazonaws.services.lambda.runtime.ClientContext;
import com.amazonaws.services.lambda.runtime.CognitoIdentity;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.assaffin.games.functions.actions.ActionHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.socket.TextMessage;

import java.util.concurrent.CopyOnWriteArraySet;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2WebSocketEvent;
import com.fasterxml.jackson.databind.ObjectMapper;

public class GameWSController extends TextWebSocketHandler {

    private final CopyOnWriteArraySet<WebSocketSession> sessions = new CopyOnWriteArraySet<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ActionHandler lambdaHandler = new ActionHandler();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.add(session); // Save the connection
        System.out.println("New player connected: " + session.getId());
        handleTextMessage(session,
                new TextMessage("{ \"action\": \"join_game\", \"username\": \"" + session.getId() + "\" }"));
    }

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        System.out.println("Received: " + payload);

        callGameActionHandlerLambda(payload, session);

        // Broadcast the message to all connected players
        for (WebSocketSession webSocketSession : sessions) {
            if (webSocketSession.isOpen()) {
                webSocketSession.sendMessage(new TextMessage(payload));
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, org.springframework.web.socket.CloseStatus status) throws Exception {
        sessions.remove(session); // Remove the closed session
        System.out.println("Player disconnected: " + session.getId());
        handleTextMessage(session,
                new TextMessage("{ \"action\": \"leave_game\", \"username\": \"" + session.getId() + "\" }"));
    }

    private void callGameActionHandlerLambda(String payload, WebSocketSession session) {
        try {
            APIGatewayV2WebSocketEvent event = new APIGatewayV2WebSocketEvent();
            event.setBody(payload);
            APIGatewayV2WebSocketEvent.RequestContext ctx = new APIGatewayV2WebSocketEvent.RequestContext();
            ctx.setConnectionId(session.getId());
            ctx.setDomainName("localhost");
            ctx.setStage("dev");
            event.setRequestContext(ctx);
            Context lambdaContext = new Context() {
                @Override public String getAwsRequestId() { return ""; }
                @Override public String getLogGroupName() { return ""; }
                @Override public String getLogStreamName() { return ""; }
                @Override public String getFunctionName() { return ""; }
                @Override public String getFunctionVersion() { return ""; }
                @Override public String getInvokedFunctionArn() { return ""; }
                @Override public CognitoIdentity getIdentity() { return null; }
                @Override public ClientContext getClientContext() { return null; }
                @Override public int getRemainingTimeInMillis() { return 0; }
                @Override public int getMemoryLimitInMB() { return 0; }
                @Override public LambdaLogger getLogger() { return new LambdaLogger() {
                    public void log(String s) { System.out.println(s); }
                    public void log(byte[] bytes) { System.out.println(new String(bytes)); }
                }; }
            };
            lambdaHandler.handleRequest(event, lambdaContext);
        } catch (Exception e) {
            System.err.println("Failed to forward to Lambda handler: " + e.getMessage());
        }
    }

}