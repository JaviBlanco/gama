package ummisco.gama.network.udp;

import java.util.Map;

import msi.gama.metamodel.agent.IAgent;
import msi.gama.runtime.IScope;
import msi.gama.util.GamaMap;
import ummisco.gama.network.skills.IConnector;

public class UDPConnector implements IConnector{

	@Override
	public void connectToServer(IScope scope, String dest, String server) throws Exception {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void sendMessage(IScope scope, String dest, Map<String, String> data) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public GamaMap<String, String> fetchMessageBox(IAgent agt) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean emptyMessageBox(IAgent agt) {
		// TODO Auto-generated method stub
		return false;
	}

}
