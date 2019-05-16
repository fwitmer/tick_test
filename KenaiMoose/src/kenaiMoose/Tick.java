package kenaiMoose;

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
	
	
	// variables for behavioral functions
	protected String name;
	protected boolean attached;
	protected int attach_count;
	protected int attach_length; // must be defined by derived class
	protected boolean delayed;
	protected int delay_count;
	protected int attach_delay; // must be defined by derived class
	protected Host host;
	
	// life cycle variables
	protected boolean female; 
	protected String life_stage;
	protected int EGG_LENGTH;
	protected int LARVA_LENGTH;
	protected int NYMPH_LENGTH;
	protected int ADULT_LENGTH;
	protected int lifecycle_counter;
	
	public Tick(String name) {
		this.name = name;
		lifecycle_counter = 0;
		attach_count = 0;
		delay_count = 0;
		attached = false;
		host = null;
		delayed = false;
		life_stage = "egg";
	
		/* For testing Tick base class
		ATTACH_LENGTH = 7;
		ATTACH_DELAY = 20;
		*/
	}
	
	@ScheduledMethod(start = 0)
	public void init() {
		context = ContextUtils.getContext(this);
		geography = (Geography)context.getProjection("Kenai");
	}
	
	@ScheduledMethod(start = 1, interval = 1)
	public void step() {
		lifecycle();
		// if Tick is attached, update position to Host's new position
		if(attached) {
			
			Coordinate newPosition = host.getCoord();
			Point newPoint = geoFac.createPoint(newPosition);
			geography.move(this, newPoint);
			// Tick has been riding Host for specified amount of time
			if (attach_count >= attach_length) {
				detach();
				return;
			}
			attach_count++;
		}
		// check if Tick has attachment delay after detaching
		else if(delayed) {
			if (delay_count >= attach_delay) {
				delayed = false;
				delay_count = 0;
				return;
			}
			delay_count++;
		}
		
	}
	
	// Abstract methods to force setting ATTACH_LENGTH and ATTACH_DELAY - protected
	protected abstract void set_attach_length(int length);
	protected abstract void set_attach_delay(int delay);
	
	public Geography getGeo() {
		return geography;
	}
	
	public boolean isAttached() {
		return attached;
	}
	
	public boolean isDelayed() {
		return delayed;
	}
	
	public boolean isFemale() {
		return female;
	}
	
	
	// Logic for attaching to Host, expected to be called by the Host to be infected
	public void attach(Host host) {
		if (!delayed) {
			attached = true;
			this.host = host;
			host.add_tick(this);
			//System.out.println(name + " attached to " + host.getName());
		}
	}
	
	// Logic for detaching from Host
	public void detach() {
		attached = false;
		delayed = true;
		attach_count = 0;
		host.remove_tick(this);
		//System.out.println(name + " detached from " + host.getName());
		host = null;
	}
	// determine what to do and update lifecycle counter
	private void lifecycle() {
		lifecycle_counter++;
		switch (life_stage) {
			case "egg":
				if (lifecycle_counter > EGG_LENGTH) {
					hatch();
				}
				break;
			case "larva":
				if (lifecycle_counter > LARVA_LENGTH) {
					molt();
				}
				break;
			case "nymph":
				if (lifecycle_counter > NYMPH_LENGTH) {
					molt();
				}
				break;
			case "adult":
				if (lifecycle_counter > ADULT_LENGTH) {
					mate();
				}
				break;
			default:
				System.out.println("\tLife cycle error: " + name + " has invalid life stage. Removing agent.");
				die();
		}
	}
	
	// TODO: utilize habitat suitability to determine hatching behavior
	private void hatch() {
		lifecycle_counter = 0;
		life_stage = "larva";
		return;
	}
	
	// TODO: utilize habitat suitability to determine molting behavior
	private void molt() {
		lifecycle_counter = 0;
		switch(life_stage) {
			case "larva":
				life_stage = "nymph";
				break;
			case "nymph":
				life_stage = "adult";
		}
	}
	
	// TODO: implement this
	private void mate() {
		die();
		return;
	}
	
	public void die() {
		context.remove(this);
		return;
	}

}
