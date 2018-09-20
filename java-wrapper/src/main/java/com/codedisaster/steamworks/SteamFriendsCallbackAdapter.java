package com.codedisaster.steamworks;

@SuppressWarnings("unused")
class SteamFriendsCallbackAdapter extends SteamCallbackAdapter<SteamFriendsCallback> {

	SteamFriendsCallbackAdapter(SteamFriendsCallback callback) {
		super(callback);
	}

	void onSetPersonaNameResponse(boolean success, boolean localSuccess, int result) {
		callback.onSetPersonaNameResponse(success, localSuccess, SteamResult.byValue(result));
	}

	void onPersonaStateChange(long steamID, int changeFlags) {		
		callback.onPersonaStateChange(new SteamID(steamID), changeFlags);
	}

	void onGameOverlayActivated(boolean active) {
		callback.onGameOverlayActivated(active);
	}

	void onGameLobbyJoinRequested(long steamIDLobby, long steamIDFriend) {
		callback.onGameLobbyJoinRequested(new SteamID(steamIDLobby), new SteamID(steamIDFriend));
	}

	void onAvatarImageLoaded(long steamID, int image, int width, int height) {
		callback.onAvatarImageLoaded(new SteamID(steamID), image, width, height);
	}

	void onFriendRichPresenceUpdate(long steamIDFriend, int appID) {
		callback.onFriendRichPresenceUpdate(new SteamID(steamIDFriend), appID);
	}

	void onGameRichPresenceJoinRequested(long steamIDFriend, String connect) {
		callback.onGameRichPresenceJoinRequested(new SteamID(steamIDFriend), connect);
	}

	void onGameServerChangeRequested(String server, String password) {
		callback.onGameServerChangeRequested(server, password);
	}
}
