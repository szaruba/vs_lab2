package channel;

public interface AuthenticatedChannel extends Channel {
	void setUser(String user);
	String getUser();
}
