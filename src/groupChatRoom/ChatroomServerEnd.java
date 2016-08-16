package groupChatRoom;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.websocket.EncodeException;
import javax.websocket.OnClose;
import javax.websocket.OnMessage;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;
import javax.websocket.OnOpen;

import java.sql.*;
@ServerEndpoint(value = "/ChatroomServerEnd", encoders = {MessageEncoder.class}, decoders = { MessageDecoder.class })
public class ChatroomServerEnd {
	/*current sessions set*/
	static Set<Session> users = Collections.synchronizedSet(new HashSet<Session>());
	/*current user names set*/
	static Set<String> onlineUsers = Collections.synchronizedSet(new HashSet<String>());
	/*Connection to MySql*/
	static Connection myConn;
	
	/*
	 * Get a connection to database
	 * return Connection myConn
	 */
	public static Connection getConnectionInstance() {
      if(myConn == null) {
    	  try {
			myConn = DriverManager.getConnection("jdbc:mysql://localhost:3306/demo","root","password");
			System.out.println("db instance");
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
      }
      return myConn;
   }
	
	
	/*
	 * @param newly created user's Session
	 */
	@OnOpen
	public void handleOpen(Session userSession){
	
	}
	
	
	/*
	 * @param ChatMessage m  Chat Message object
	 * @param Session userSession  message sender's session
	 */
	@OnMessage
	public void handleMessage(ChatMessage m, Session userSession) throws IOException,EncodeException{
		String username = (String) userSession.getUserProperties().get("username");
		System.out.println(username);
		
		java.util.Date time = new java.util.Date();
		sendResponse(m,userSession, username,time);
		try {
			getConnectionInstance();
			PreparedStatement preparedStmt;
			String query="";
			/*Existing user is sending non-empty message*/
			if(username != null && m.getMessage() != null && !(m.getMessage().isEmpty())){
				query = "insert into chat_log (timestamp, user, message)" 
						+ " values (?, ?, ?)";
				
				preparedStmt = myConn.prepareStatement(query);
				preparedStmt.setTimestamp (1, new java.sql.Timestamp(time.getTime()));
				preparedStmt.setString   (2, username);
				preparedStmt.setString(3, m.getMessage());
				preparedStmt.execute();
				
			}else if(username == null){
				/*New user is sending user name*/
				query = "insert into log (name, activity_timestamp, login)"
						+" values (? ,? ,?)";
				preparedStmt = myConn.prepareStatement(query);
				preparedStmt.setString (1, m.getMessage());
				preparedStmt.setTimestamp   (2, new java.sql.Timestamp(time.getTime()));
				preparedStmt.setBoolean(3, true);
				preparedStmt.execute();
				
			}
			// the mysql insert statement
			System.out.println("executed");
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}			
	}
	
	
	/*
	 * @param java.util.Date time  current time in MySql datetime format
	 * @param String username      message sender's name
	 */
	
	private void sendResponse(ChatMessage m, Session userSession,String username, java.util.Date time) throws IOException,EncodeException{
		ChatMessage outgoingMessage = new ChatMessage();
		/*New user need to get a response*/
		if(username == null){
			/*User name has already been taken, not valid for use*/
			if(onlineUsers.contains(m.getMessage())){
				outgoingMessage.setName("System");
				outgoingMessage.setMessage("User "+ m.getMessage() 
						+" has logged in already, please choose a different name");
				outgoingMessage.setType('4');
				userSession.getBasicRemote().sendObject(outgoingMessage);
				
			}else{
				/*User name is valid and this is a real message, not button click*/
				if(m.getType() == 48){
					users.add(userSession);
					onlineUsers.add(m.getMessage());
					Iterator<Session> iterator = users.iterator();
					/*broadcast the new user's name to all other users to update the right column of online users*/
					while(iterator.hasNext()){
						iterator.next().getBasicRemote().sendText(buildJsonData());
						
					}
				}
				/*Send welcome message to new user*/
				userSession.getUserProperties().put("username", m.getMessage());
				outgoingMessage.setName("System");
				outgoingMessage.setMessage("Welcome " + m.getMessage());
				userSession.getBasicRemote().sendObject(outgoingMessage);
				
			}	
		}else{
			/*message comes from existing user*/
			if(m.getType() == 48){
				/*normal message needs to be broadcasted*/
				broadcast(m, outgoingMessage,username);
				
			}else if(m.getType() == 49){
				/*get all message after this user's registry*/
				try {
					loadAllMessages(outgoingMessage, userSession, username);
				} catch (SQLException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
			}else{
				/*get unread message*/
				try {
					loadUnreadMessages(outgoingMessage, userSession, username);
				} catch (SQLException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
	}
	
	/*
	 * @param ChatMessage m    Normal message to be sent to all online users
	 * @param ChatMessage outgointingMessage    formatted object with name and message attribute
	 * @param String username  Message sender's name
	 */
	private void broadcast(ChatMessage m,ChatMessage outgoingMessage, String username) throws IOException, EncodeException{
		outgoingMessage.setName(username);
		outgoingMessage.setMessage(m.getMessage());
		Iterator<Session> iterator = users.iterator();
		/*Broadcasting to all online users*/
		while(iterator.hasNext()){
			iterator.next().getBasicRemote().sendObject(outgoingMessage);
		}
		
	}
	
	
	/*
	 * All Messages that have been sent since the requesting user's registry
	 * Time stamp should be larger than the first login's time stamp
	 */
	private void loadAllMessages(ChatMessage outgoingMessage, Session userSession, String username) throws IOException, EncodeException, SQLException{
		Statement stmt = getConnectionInstance().createStatement();
		String query = "SELECT user, message FROM demo.chat_log WHERE timestamp > "+
					   "(SELECT MIN(activity_timestamp) FROM demo.log WHERE name = '"+username+"')";
		
		ResultSet rs = stmt.executeQuery(query);
		
		while(rs.next()){
			outgoingMessage.setName(rs.getString("user"));
			outgoingMessage.setMessage(rs.getString("message"));
			outgoingMessage.setType('2');
			userSession.getBasicRemote().sendObject(outgoingMessage);
		}	
		
	}
	
	
	
	/*
	 * If the user has logged out at least once before, unread messages are after the last logout
	 * If the user just logged in for the first time, unread messages are all messages available in database
	 * Unread messages' time stamps are between the last logout and last login
	 */
	private void loadUnreadMessages(ChatMessage outgoingMessage, Session userSession, String username) throws IOException, EncodeException, SQLException{
		Statement stmt = getConnectionInstance().createStatement();
		String query = "SELECT user, message FROM demo.chat_log WHERE timestamp < " 
		+ "(SELECT MAX(activity_timestamp) FROM demo.log WHERE name = '"+username+"' AND login = '1')";
		String hasLoggedOut = "SELECT COUNT(*) FROM demo.log WHERE name = '"+username+"'AND login = '0'";
		ResultSet loggedOutRecord = stmt.executeQuery(hasLoggedOut);
		loggedOutRecord.next();
		long count = loggedOutRecord.getLong(1);
		/*if the user has ever logged out, filter out only messages after last logout*/
		if(count > 0){
			query += "AND timestamp > "+
			   "(SELECT MAX(activity_timestamp) FROM demo.log WHERE name = '"+username+"' AND login = '0')";
		}
		//System.out.println(query);
		ResultSet rs = stmt.executeQuery(query);
		/*send out every messages that match for the logic*/
		while(rs.next()){
			outgoingMessage.setName(rs.getString("user"));
			outgoingMessage.setMessage(rs.getString("message"));
			outgoingMessage.setType('3');
			userSession.getBasicRemote().sendObject(outgoingMessage);
		}
	}
	
	
	/*
	 * Put all online users' name into array and encode them to json
	 * return encoded json string
	 */
	private String buildJsonData() {
		Iterator<String> iterator = onlineUsers.iterator();
		JsonArrayBuilder jsonArrayBuilder = Json.createArrayBuilder();
		while(iterator.hasNext()){
			jsonArrayBuilder.add((String)iterator.next());
		}
		return Json.createObjectBuilder().add("users",jsonArrayBuilder).build().toString();
	}
	
	
	/*
	 * @param Session userSession		Ended session
	 * Broadcast to all remaining user the updated user list
	 * Write to database about the logout event
	 * 
	 */
	@OnClose
	public void handleClose(Session userSession) throws IOException{
		if(userSession != null && !users.isEmpty() && users.contains(userSession)){
			users.remove(userSession);
			
			String username = (String) userSession.getUserProperties().get("username");
			java.util.Date time = new java.util.Date();
			onlineUsers.remove(username);
			Iterator<Session> iterator = users.iterator();
			/*sent to all remaining users the updated user list*/
			while(iterator.hasNext()){
				iterator.next().getBasicRemote().sendText(buildJsonData());
			}
			try {
				/*insert the log out event*/
				getConnectionInstance();
				PreparedStatement preparedStmt;
				
				String query = "insert into log (name, activity_timestamp, login)"+" values (? ,? ,?)";
				preparedStmt = myConn.prepareStatement(query);
				preparedStmt.setString (1, username);
				preparedStmt.setTimestamp   (2, new java.sql.Timestamp(time.getTime()));
				preparedStmt.setBoolean(3, false);
				
				preparedStmt.execute();
			} catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}	
	}
}

