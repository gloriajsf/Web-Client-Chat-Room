package groupChatRoom;


import java.io.StringReader;

import javax.json.Json;
import javax.websocket.DecodeException;
import javax.websocket.Decoder;
import javax.websocket.EndpointConfig;

public class MessageDecoder implements Decoder.Text<ChatMessage>{

	@Override
	public void destroy() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void init(EndpointConfig arg0) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public ChatMessage decode(String m) throws DecodeException {
		// TODO Auto-generated method stub
		ChatMessage cm = new ChatMessage();
		cm.setMessage((Json.createReader(new StringReader(m)).readObject()).getString("message"));
		cm.setType((Json.createReader(new StringReader(m)).readObject()).getString("type").charAt(0));
		return cm;
	}

	@Override
	public boolean willDecode(String m) {
		boolean canDecode = true;
		try{
			Json.createReader(new StringReader(m)).readObject();
		}catch(Exception e){
			canDecode = false;
		}
		// TODO Auto-generated method stub
		return canDecode;
	}	

}
