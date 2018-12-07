package gov.usgs.aqcu.model;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.aquaticinformatics.aquarius.sdk.timeseries.servicemodels.Publish.Qualifier;

public class ExtremesMinMax {
	public static final String MIN_MAX_POINTS_KEY = "points";

	private Map<String, List<ExtremesPoint>> min;
	private Map<String, List<ExtremesPoint>> max;
	private List<Qualifier> qualifiers;
	
	public Map<String, List<ExtremesPoint>> getMin() {
		return min;
	}
	
	public void setMin(Map<String, List<ExtremesPoint>> val) {
		this.min = val;
	}
	
	public Map<String, List<ExtremesPoint>> getMax() {
		return max;
	}
	
	public void setMax(Map<String, List<ExtremesPoint>> val) {
		this.max = val;
	}
	
	public List<Qualifier> getQualifiers() {
		return qualifiers;
	}
	
	public void setQualifiers(List<Qualifier> val) {
		this.qualifiers = val;
	}

	public void setMinPoints(List<ExtremesPoint> points) {
		if(points != null && !points.isEmpty()) {
			if(min == null) {
				min = new HashMap<>();
			}
			min.put(MIN_MAX_POINTS_KEY, points);
		}
	}

	public void setMaxPoints(List<ExtremesPoint> points) {
		if(points != null && !points.isEmpty()) {
			if(max == null) {
				max = new HashMap<>();
			}
			max.put(MIN_MAX_POINTS_KEY, points);
		}
	}

	public void setMinRelatedPoints(List<ExtremesPoint> points, String relatedKey) {
		if(points != null && !points.isEmpty()) {
			if(min == null) {
				min = new HashMap<>();
			}
			min.put(relatedKey, points);
		}
	}

	public void setMaxRelatedPoints(List<ExtremesPoint> points, String relatedKey) {
		if(points != null && !points.isEmpty()) {
			if(max == null) {
				max = new HashMap<>();
			}
			max.put(relatedKey, points);
		}
	}
}
	
