/***************************************************************************
*                                                                          *
* Panako - acoustic fingerprinting                                         *
* Copyright (C) 2014 - 2022 - Joren Six / IPEM                             *
*                                                                          *
* This program is free software: you can redistribute it and/or modify     *
* it under the terms of the GNU Affero General Public License as           *
* published by the Free Software Foundation, either version 3 of the       *
* License, or (at your option) any later version.                          *
*                                                                          *
* This program is distributed in the hope that it will be useful,          *
* but WITHOUT ANY WARRANTY; without even the implied warranty of           *
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the            *
* GNU Affero General Public License for more details.                      *
*                                                                          *
* You should have received a copy of the GNU Affero General Public License *
* along with this program.  If not, see <http://www.gnu.org/licenses/>     *
*                                                                          *
****************************************************************************
*    ______   ________   ___   __    ________   ___   ___   ______         *
*   /_____/\ /_______/\ /__/\ /__/\ /_______/\ /___/\/__/\ /_____/\        *
*   \:::_ \ \\::: _  \ \\::\_\\  \ \\::: _  \ \\::.\ \\ \ \\:::_ \ \       *
*    \:(_) \ \\::(_)  \ \\:. `-\  \ \\::(_)  \ \\:: \/_) \ \\:\ \ \ \      *
*     \: ___\/ \:: __  \ \\:. _    \ \\:: __  \ \\:. __  ( ( \:\ \ \ \     *
*      \ \ \    \:.\ \  \ \\. \`-\  \ \\:.\ \  \ \\: \ )  \ \ \:\_\ \ \    *
*       \_\/     \__\/\__\/ \__\/ \__\/ \__\/\__\/ \__\/\__\/  \_____\/    *
*                                                                          *
****************************************************************************
*                                                                          *
*                              Panako                                      *
*                       Acoustic Fingerprinting                            *
*                                                                          *
****************************************************************************/

package be.panako.strategy.olaf;

/**
 * A data class representing  matching fingerprints
 */
public class OlafMatch {

	/**
	 * Initialize an empty match.
	 */
	public OlafMatch(){}

	/**
	 * The hash in the reference database which should be near to the query hash
	 */
	public long matchedNearHash;

	/**
	 * The query hash
	 */
	public long originalHash;

	/**
	 * The match audio identifier
	 */
	public int identifier;
	
	/**
	 * Time in blocks in the original, matched audio.
	 */
	public int matchTime;
	
	/**
	 * Time in blocks in the query.
	 */
	public int queryTime;

	/**
	 * The time difference between the indexed and query times
	 * @return The time difference between the indexed and query times
	 */
	public int deltaT() {
		return matchTime - queryTime;
	}
	
	public String toString() {
		return String.format("%d %d %d %d", identifier, matchTime, queryTime, deltaT());
	}
}
