/* *********************************************************************** *
 * project: org.matsim.*
 * DgOtfLinkLanesAgentsNoParkingHandler
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2009 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */
package playground.dgrether.signalVis.io;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.apache.log4j.Logger;
import org.matsim.core.utils.misc.ByteBufferUtils;
import org.matsim.vis.otfvis.caching.SceneGraph;
import org.matsim.vis.otfvis.data.OTFDataReceiver;
import org.matsim.vis.otfvis.interfaces.OTFDataReader;

import playground.dgrether.signalVis.drawer.DgSimpleQuadDrawer;


/**
 * @author dgrether
 *
 */
public class DgOtfLaneReader extends OTFDataReader {

	private static final Logger log = Logger.getLogger(DgOtfLaneReader.class);
	
	private DgSimpleQuadDrawer drawer;
	
	public DgOtfLaneReader() {
	}

	@Override
	public void readConstData(ByteBuffer in) throws IOException {
		String id = ByteBufferUtils.getString(in);
		this.drawer.setQuad(in.getFloat(), in.getFloat(),in.getFloat(), in.getFloat(), in.getInt());
		this.drawer.setId(id.toCharArray());

		int nrToNodeLanes = in.getInt();
		drawer.setNumberOfLanes(nrToNodeLanes);
		log.debug("reader numberoftonodelanes: " + nrToNodeLanes);
		if (nrToNodeLanes != 1) {
			this.drawer.setBranchPoint(in.getDouble(), in.getDouble());
			for (int i = 0; i < nrToNodeLanes; i++){
				this.drawer.addNewQueueLaneData(ByteBufferUtils.getString(in), in.getDouble(), in.getDouble());
			}
		}
		
	}

	@Override
	public void connect(OTFDataReceiver receiver) {
		this.drawer = (DgSimpleQuadDrawer) receiver;
	}

	@Override
	public void invalidate(SceneGraph graph) {
		this.drawer.invalidate(graph);
	}

	@Override
	public void readDynData(ByteBuffer in, SceneGraph graph) throws IOException {
		// nothing to do as lanes are non dynamical data
	}
	

	
	
}
