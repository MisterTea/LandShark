package com.github.mistertea.boardgame.landshark.player;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.github.mistertea.boardgame.landshark.PlayerState;
import com.github.mistertea.boardgame.landshark.State;
import com.github.mistertea.boardgame.landshark.StateQuery;

public class RLStateMaker {
	public byte[] makeAuctionState(StateQuery query, State inputState,
			String playerName) {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		PlayerState playerState = query.getPlayerState(inputState, playerName);

		{
			byte logPlayerMoney = 0;
			if (playerState.cash > 0) {
				logPlayerMoney = (byte) Math.log(playerState.cash);
			}
			baos.write(logPlayerMoney);
		}

		// Get the other players' money
		{
			List<Byte> logOtherPlayerMoney = new ArrayList<Byte>();
			for (PlayerState otherPlayerState : inputState.playerStates) {
				if (otherPlayerState == playerState) {
					continue;
				}

				byte b = 0;
				if (query.isActivePlayer(otherPlayerState)
						&& otherPlayerState.cash > 0) {
					b = (byte) Math.log(otherPlayerState.cash);
				}
				logOtherPlayerMoney.add(b);
			}
			while (logOtherPlayerMoney.size() < 4) {
				// Pad with 0's
				logOtherPlayerMoney.add((byte) 0);
			}

			Collections.sort(logOtherPlayerMoney);

			// Reversing shouldn't affect the algorithm, but is done for
			// readability.
			Collections.reverse(logOtherPlayerMoney);
		}

		baos.write((byte) query.countUnownedProperties(inputState));

		{
			int rentEstimate = 100; // TODO: Better estimate rent
			byte logRentEstimate = 0;
			if (rentEstimate > 0) {
				logRentEstimate = (byte) Math.log(rentEstimate);
			}
			baos.write(logRentEstimate);
		}

		return baos.toByteArray();
	}

	public byte[] makeBuyHousesState(StateQuery query, State inputState,
			String playerName) {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		PlayerState playerState = query.getPlayerState(inputState, playerName);

		{
			byte logPlayerMoney = 0;
			if (playerState.cash > 0) {
				logPlayerMoney = (byte) Math.log(playerState.cash);
			}
			baos.write(logPlayerMoney);
		}

		// Get the other players' money
		{
			List<Byte> logOtherPlayerMoney = new ArrayList<Byte>();
			for (PlayerState otherPlayerState : inputState.playerStates) {
				if (otherPlayerState == playerState) {
					continue;
				}

				byte b = 0;
				if (query.isActivePlayer(otherPlayerState)
						&& otherPlayerState.cash > 0) {
					b = (byte) Math.log(otherPlayerState.cash);
				}
				logOtherPlayerMoney.add(b);
			}
			while (logOtherPlayerMoney.size() < 4) {
				// Pad with 0's
				logOtherPlayerMoney.add((byte) 0);
			}

			Collections.sort(logOtherPlayerMoney);

			// Reversing shouldn't affect the algorithm, but is done for
			// readability.
			Collections.reverse(logOtherPlayerMoney);
		}

		baos.write((byte) query.countUnownedProperties(inputState));

		{
			int rentEstimate = 100; // TODO: Fix
			byte logRentEstimate = 0;
			if (rentEstimate > 0) {
				logRentEstimate = (byte) Math.log(rentEstimate);
			}
			baos.write(logRentEstimate);
		}

		return baos.toByteArray();
	}
}
