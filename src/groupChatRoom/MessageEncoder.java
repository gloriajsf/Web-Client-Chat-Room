package groupChatRoom;

import javax.json.Json;
import javax.websocket.EncodeException;
import javax.websocket.Encoder;
import javax.websocket.EndpointConfig;

public class MessageEncoder implements Encoder.Text<ChatMessage>{

	@Override
	public void destroy() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void init(EndpointConfig arg0) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public String encode(ChatMessage m) throws EncodeException {
		// TODO Auto-generated method stub
		
		return Json.createObjectBuilder().add("name", m.getName())
				.add("message",m.getMessage()).add("type", m.getType()).build().toString();
	}

}
