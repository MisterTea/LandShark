namespace java com.github.mistertea.boardgame.landshark
namespace scala com.github.mistertea.boardgame.landshark.scala

include "core.thrift"
include "landshark.thrift"

service LandsharkService {
	string newGame(1:list<string> playerIds);
	void addInput(1:string playerId, 2:string inputData);
	list<landshark.State> getStates(1:i32 startIndex);
}
