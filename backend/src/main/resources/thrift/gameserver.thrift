namespace java com.github.mistertea.boardgame.landshark

include "core.thrift"
include "landshark.thrift"

service LandsharkService {
	bool sendCommand(1:string playerId, 2:string gameId, 3:landshark.LandsharkCommand command);
}
