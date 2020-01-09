package kenaiMoose;

import java.util.Random;

import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.geometry.DirectPosition2D;
import org.opengis.geometry.DirectPosition;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Point;

import repast.simphony.context.Context;
import repast.simphony.engine.schedule.ScheduleParameters;
import repast.simphony.engine.schedule.ScheduledMethod;
import repast.simphony.space.gis.Geography;
import repast.simphony.util.ContextUtils;

public abstract class Tick {
	
	// variables for holding the framework objects
	protected Context context;
	protected Geography geography;
	protected GeometryFactory geoFac = new GeometryFactory();
	protected static GridCoverage2D suitability_raster;
	
	
	// variables for behavioral functions
	protected String name;
	protected boolean attached;
	protected int attach_count;
	protected int attach_length; // must be defined by derived class
	protected int attach_delay; // must be defined by derived class
	protected Host host;
	
	// life cycle variables
	protected static String START_LIFE_CYCLE; // static variable for defining what stage Ticks should start at during init
	protected boolean female;  // true if tick is female
	protected String life_stage; // holder state in life stage
	protected static int EGG_LENGTH; // average length of time before egg hatches
	protected static int LARVA_LENGTH; // average length of time before larval mortality
	protected static int LARVA_FEED_LENGTH; // larval length of attachment for feeding
	protected static int NYMPH_LENGTH; // average length of time before nympth mortality
	protected static int NYMPH_FEED_LENGTH; // nymphal length of attachment for feeding
	protected static int ADULT_LENGTH; // average length of time before adult mortality
	protected static int ADULT_FEED_LENGTH; // adult length of attachment for feeding (females only)
	protected static int EGG_COUNT;
	protected int lifecycle_counter; // basic counter used to count steps in all stages of lifecycle behaviors
	protected boolean has_fed; // marker for whether or not tick has successfully fed at current life stage
	
	public Tick(String name) {
		this.name = name;
		determine_sex();
		lifecycle_counter = 0;
		attach_count = 0;
		attached = false;
		host = null;
		has_fed = false;
		life_stage = START_LIFE_CYCLE;
	}
	
	// additional constructor for defining life stage
	public Tick(String name, String life_stage) {
		this.name = name;
		this.life_stage = life_stage;
		determine_sex();
		lifecycle_counter = 0;
		attach_count = 0;
		attached = false;
		host = null;
		has_fed = false;
	}
	
	@ScheduledMethod(start = 0)
	public void init() {
		context = ContextUtils.getContext(this);
		geography = (Geography)context.getProjection("Kenai");
		System.out.println(this.name + " habitat sample: " + habitat_sample());
	}
	
	public static void setSuitability(GridCoverage2D raster) {
		suitability_raster = raster;
		return;
	}
	
	
	public Coordinate getCoord() {
		return new Coordinate(geography.getGeometry(this).getCoordinate());
	}
	
	//Get lat and long for data sets
	
	public double getLong() {
		Coordinate coord = getCoord();
		return coord.x;
	}

	public double getLat() {
		Coordinate coord = getCoord();
		return coord.y;
	}
	
	public String getLifestate() {
		return this.life_stage;
	}
	
	
	@ScheduledMethod(start = 1, interval = 1)
	public void step() {
		// if Tick is attached, update position to Host's new position
		if(attached) {
			
			Coordinate newPosition = host.getCoord();
			Point newPoint = geoFac.createPoint(newPosition);
			geography.move(this, newPoint);
			// Tick has been riding Host for specified amount of time
			// 	 (been attached long enough)  &&    (not an adult) - adult detachment behavior handled in lifecycle()
			if (attach_count >= attach_length && !life_stage.equals("adult") ) {
				detach();
			}
			else
				attach_count++;
		}
		lifecycle();
		
	}
	
	@ScheduledMethod(start = 90, interval = 90)
	public void skip_inactive_period() {
		double prob_death = 1 - habitat_sample(); 
		double prob_death_per_day = prob_death / 365;
		
		if (Math.random() < (prob_death_per_day * 275) )
			die();
		lifecycle_counter += 275;
		return;
	}
	
	// Abstract methods to force setting ATTACH_LENGTH and ATTACH_DELAY - protected
	protected abstract void set_attach_length(String lifecycle);
	
	public Geography getGeo() {
		return geography;
	}
	
	public boolean isAttached() {
		return attached;
	}
	
	public boolean isFemale() {
		return female;
	}
	
	public Host getHost() {
		return host;
	}
	
	public static void setStartStage(String stage) {
		START_LIFE_CYCLE = stage;
		return;
	}
	
	
	// Logic for attaching to Host, expected to be called by the Host to be infected
	public boolean attach(Host host) {
		if (!has_fed) {
			switch(life_stage) {
				case "larva": // don't attach to Moose
					if (!(host instanceof SmHost))
						return false;
					break;
				case "nymph": // don't attach to Moose
					if (!(host instanceof SmHost))
						return false;
					break;
				case "adult": // preference for large mammals
					if (!(host instanceof Moose))
						return false;
					break;
				default: // egg doesn't attach
					return false;
			}
			// if we got here, tick is hungry and found an appropriate host
			attached = true;
			has_fed = true;
			this.host = host;
			host.add_tick(this);
			return true;
			//System.out.println(name + " attached to " + host.getName());
		}
		// adult males will attach regardless
		else if (life_stage.equals("adult") && !female) {
			attached = true;
			this.host = host;
			host.add_tick(this);
			return true;
		}
		return false;
	}
	
	// Logic for detaching from Host
	public void detach() {
		attached = false;
		attach_count = 0;
		host.remove_tick(this);
		//System.out.println(name + " detached from " + host.getName());
		host = null;
	}
	
	// set male or female
	private void determine_sex() {
		Random rnd = new Random();
		switch(rnd.nextInt(2)) {
			case 0: // female
				female = true;
				break;
			case 1: // male
				female = false;
				break;
		}
	}
	
	// determine what to do and update lifecycle counter
	private void lifecycle() {
		lifecycle_counter++;
		double prob_death = 1 - habitat_sample(); 
		double prob_death_per_day = prob_death / 365;
		if (Math.random() < prob_death_per_day) 
			die();
		
		switch (life_stage) {
			case "egg":
				if (lifecycle_counter > EGG_LENGTH)
					hatch();
				break;
			case "larva":
				// didn't feed in time, die
				if (!has_fed && lifecycle_counter > LARVA_LENGTH) {
					die();
					return;
				}
				// still feeding, do nothing this step
				if(attached) 
					break;
				// fed and ready to molt
				if (has_fed) {
					molt();
					break;
				}
				
				break;
			case "nymph":
				if (!has_fed && lifecycle_counter > NYMPH_LENGTH) {
					die();
					return;
				}
				
				if(attached)
					break;
				
				
				if(has_fed) {
					molt();
					break;
				}
			case "adult": 
				if(female) {
					// TODO: female behaviors happen here
					if (!has_fed && lifecycle_counter > ADULT_LENGTH) {
						die();
						return;
					}
				}
				else {
					// TODO: male behaviors happen here (does not feed)
					if (lifecycle_counter > ADULT_LENGTH) {
						die();
						return;
					}
					if (attached) {
						// TODO: need to look for a mate here, continue until dying
						break;
					}
				}
				break;
			default:
				System.out.println("\tLife cycle error: " + name + " has invalid life stage. Removing agent.");
				die();
		}
	}
	
	private void hatch() {
		lifecycle_counter = 0;
		has_fed = false;
		life_stage = "larva";
		set_attach_length(life_stage);
		return;
	}
	
	private void molt() {
		lifecycle_counter = 0;
		has_fed = false;
		switch(life_stage) {
			case "larva":
				life_stage = "nymph";
				set_attach_length(life_stage);
				break;
			case "nymph":
				life_stage = "adult";
				set_attach_length(life_stage);
				break;
		}
	}
	
	// TODO: implement this
	protected abstract void mate();
	
	public void die() {
		if (attached) {
			detach();
		}
		context.remove(this);
		return;
	}
	
	public double habitat_sample() {
		Coordinate coord = new Coordinate(geography.getGeometry(this).getCoordinate());
		DirectPosition position = new DirectPosition2D(geography.getCRS(), coord.x, coord.y);
		double[] sample = (double[]) suitability_raster.evaluate(position);
		return sample[0];
	}
}
