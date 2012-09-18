/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2012 by the members listed in the COPYING,        *
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

/**
 * 
 */
package playground.kai.run;

import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;

import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.controler.corelisteners.PlansScoring;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.core.mobsim.qsim.ActivityEngine;
import org.matsim.core.mobsim.qsim.QSim;
import org.matsim.core.mobsim.qsim.TeleportationEngine;
import org.matsim.core.mobsim.qsim.agents.AgentFactory;
import org.matsim.core.mobsim.qsim.agents.DefaultAgentFactory;
import org.matsim.core.mobsim.qsim.agents.PopulationAgentSource;
import org.matsim.core.mobsim.qsim.qnetsimengine.QNetsimEngine;
import org.matsim.core.population.PopulationFactoryImpl;
import org.matsim.core.population.routes.ModeRouteFactory;
import org.matsim.core.router.ModularPlanRouter;
import org.matsim.core.router.NetworkLegRouter;
import org.matsim.core.router.PseudoTransitLegRouter;
import org.matsim.core.router.TeleportationLegRouter;
import org.matsim.core.router.costcalculators.FreespeedTravelTimeAndDisutility;
import org.matsim.core.router.costcalculators.TravelTimeAndDistanceBasedTravelDisutility;
import org.matsim.core.router.util.DijkstraFactory;
import org.matsim.core.router.util.LeastCostPathCalculatorFactory;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.scoring.ScoringFunctionFactory;
import org.matsim.core.scoring.functions.CharyparNagelScoringFunctionFactory;
import org.matsim.core.trafficmonitoring.TravelTimeCalculator;
import org.matsim.vis.otfvis.OTFFileWriterFactory;
import org.matsim.vis.snapshotwriters.SnapshotWriter;
import org.matsim.vis.snapshotwriters.SnapshotWriterFactory;
import org.matsim.vis.snapshotwriters.SnapshotWriterManager;

/**
 * @author nagel
 *
 */
public class SimplifiedControlerUtils {

	/**
	 * Convenience method to have a default implementation of the scoring.  It is deliberately non-configurable.  It makes
	 * no promises about what it does, nor about stability over time.  May be used as a starting point for own variants.
	 */
	static PlansScoring createPlansScoringDefault( Scenario sc, EventsManager ev, OutputDirectoryHierarchy controlerIO ) {
		Config config = sc.getConfig();
		Network network = sc.getNetwork() ;
		ScoringFunctionFactory scoringFunctionFactory = new CharyparNagelScoringFunctionFactory( config.planCalcScore(), network );
		final PlansScoring plansScoring = new PlansScoring( sc, ev, controlerIO, scoringFunctionFactory );
		return plansScoring;
	}

	/**
	 * Convenience method to provide a default routing algorithm.  Design thoughts:<ul>
	 * <li> This is deliberately not configurable.  If you need configuration, copy this method and re-write.
	 * <li> This takes deliberately the Scenario as input.  It will do something with it, but there are no promises what 
	 * it does.  Including no promise that results will remain stable over time.
	 * <li> A TravelTimeCalculator argument is included, since it needs to be added as an events handler, and we do not want
	 * to do this as a side effect.
	 * </ul>
	 * May be used as starting point for own variants.
	 */
	static ModularPlanRouter createRoutingAlgorithmDefault( Scenario sc, TravelTimeCalculator travelTime ) {
		Population population = sc.getPopulation() ;
		Network network = sc.getNetwork() ;
		Config config = sc.getConfig() ;
		
		// factory to generate routes:
		final ModeRouteFactory routeFactory = ((PopulationFactoryImpl) (population.getFactory())).getModeRouteFactory();
	
		// travel disutility (generalized cost)
		final TravelDisutility travelDisutility = new TravelTimeAndDistanceBasedTravelDisutility(travelTime, config.planCalcScore());
		
		final FreespeedTravelTimeAndDisutility ptTimeCostCalc = new FreespeedTravelTimeAndDisutility(-1.0, 0.0, 0.0);
	
		// define the factory for the "computer science" router.  Needs to be a factory because it might be used multiple
		// times (e.g. for car router, pt router, ...)
		final LeastCostPathCalculatorFactory leastCostPathFactory = new DijkstraFactory();
	
		// plug it together
		final ModularPlanRouter plansCalcRoute = new ModularPlanRouter();
		
		Collection<String> networkModes = config.plansCalcRoute().getNetworkModes();
		for (String mode : networkModes) {
			plansCalcRoute.addLegHandler(mode, new NetworkLegRouter(network, 
					leastCostPathFactory.createPathCalculator(network, travelDisutility, travelTime), routeFactory));
		}
		Map<String, Double> teleportedModeSpeeds = config.plansCalcRoute().getTeleportedModeSpeeds();
		for (Entry<String, Double> entry : teleportedModeSpeeds.entrySet()) {
			plansCalcRoute.addLegHandler(entry.getKey(), new TeleportationLegRouter(routeFactory, entry.getValue(), 
					config.plansCalcRoute().getBeelineDistanceFactor()));
		}
		Map<String, Double> teleportedModeFreespeedFactors = config.plansCalcRoute().getTeleportedModeFreespeedFactors();
		for (Entry<String, Double> entry : teleportedModeFreespeedFactors.entrySet()) {
			plansCalcRoute.addLegHandler(entry.getKey(), 
					new PseudoTransitLegRouter(network, leastCostPathFactory.createPathCalculator(network, ptTimeCostCalc, ptTimeCostCalc), 
							entry.getValue(), config.plansCalcRoute().getBeelineDistanceFactor(), routeFactory));
		}
		
		// return it:
		return plansCalcRoute;
	}

	/**
	 * Convenience method to have a default implementation of the mobsim.  It is deliberately non-configurable.  It makes
	 * no promises about what it does, nor about stability over time.  May be used as a starting point for own variants.
	 */
	static void runMobsimDefault(Scenario sc, EventsManager ev, int iteration, OutputDirectoryHierarchy controlerIO ) {
		QSim qSim = new QSim( sc, ev ) ;
		ActivityEngine activityEngine = new ActivityEngine();
		qSim.addMobsimEngine(activityEngine);
		qSim.addActivityHandler(activityEngine);
		QNetsimEngine netsimEngine = new QNetsimEngine(qSim, MatsimRandom.getRandom());
		qSim.addMobsimEngine(netsimEngine);
		qSim.addDepartureHandler(netsimEngine.getDepartureHandler());
		TeleportationEngine teleportationEngine = new TeleportationEngine();
		qSim.addMobsimEngine(teleportationEngine);
		AgentFactory agentFactory = new DefaultAgentFactory(qSim);
	    PopulationAgentSource agentSource = new PopulationAgentSource(sc.getPopulation(), agentFactory, qSim);
	    qSim.addAgentSource(agentSource);
		if (sc.getConfig().controler().getWriteSnapshotsInterval() != 0 && iteration % sc.getConfig().controler().getWriteSnapshotsInterval() == 0) {
			// yyyy would be nice to have the following encapsulated in some way:
			// === begin ===
			SnapshotWriterManager manager = new SnapshotWriterManager(sc.getConfig());
			SnapshotWriterFactory snapshotWriterFactory = new OTFFileWriterFactory() ;
			String baseFileName = snapshotWriterFactory.getPreferredBaseFilename();
			String fileName = controlerIO.getIterationFilename(iteration, baseFileName);
			SnapshotWriter snapshotWriter = snapshotWriterFactory.createSnapshotWriter(fileName, sc);
			manager.addSnapshotWriter(snapshotWriter);
			// === end ===
			qSim.addQueueSimulationListeners(manager);
		}
		qSim.run();
	}

}
