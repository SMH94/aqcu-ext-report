package gov.usgs.aqcu.builder;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.aquaticinformatics.aquarius.sdk.timeseries.servicemodels.Publish.Qualifier;
import com.aquaticinformatics.aquarius.sdk.timeseries.servicemodels.Publish.TimeSeriesDataServiceResponse;
import com.aquaticinformatics.aquarius.sdk.timeseries.servicemodels.Publish.TimeSeriesDescription;
import com.aquaticinformatics.aquarius.sdk.timeseries.servicemodels.Publish.TimeSeriesPoint;

import gov.usgs.aqcu.model.ExtremesMinMax;
import gov.usgs.aqcu.model.ExtremesPoint;
import gov.usgs.aqcu.model.ExtremesQualifier;
import gov.usgs.aqcu.model.ExtremesReport;
import gov.usgs.aqcu.model.ExtremesReportMetadata;
import gov.usgs.aqcu.model.TimeSeriesMinMax;
import gov.usgs.aqcu.parameter.ExtremesRequestParameters;
import gov.usgs.aqcu.retrieval.LocationDescriptionListService;
import gov.usgs.aqcu.retrieval.QualifierLookupService;
import gov.usgs.aqcu.retrieval.TimeSeriesDataService;
import gov.usgs.aqcu.retrieval.TimeSeriesDescriptionListService;
import gov.usgs.aqcu.util.AqcuTimeUtils;
import gov.usgs.aqcu.util.LogReportExecutionTime;
import gov.usgs.aqcu.util.LogStep;
import gov.usgs.aqcu.util.TimeSeriesUtils;

@Service
public class ReportBuilderService {
	
	public static final String REPORT_TITLE = "Extremes";
	public static final String REPORT_TYPE = "extremes";
	public static final String PRIMARY_RELATED_KEY = "relatedPrimary";
	public static final String UPCHAIN_RELATED_KEY = "relatedUpchain";

	private LocationDescriptionListService locationDescriptionListService;
	private MinMaxBuilderService minMaxBuilderService;
	private TimeSeriesDescriptionListService timeSeriesDescriptionListService;
	private TimeSeriesDataService timeSeriesDataService;
	private QualifierLookupService qualifierLookupService;

	@Autowired
	public ReportBuilderService(
		LocationDescriptionListService locationDescriptionListService,
		MinMaxBuilderService minMaxBuilderService,
		TimeSeriesDescriptionListService timeSeriesDescriptionListService,
		TimeSeriesDataService timeSeriesDataService,
		QualifierLookupService qualifierLookupService) {
		this.locationDescriptionListService = locationDescriptionListService;
		this.minMaxBuilderService = minMaxBuilderService;
		this.timeSeriesDescriptionListService = timeSeriesDescriptionListService;
		this.timeSeriesDataService = timeSeriesDataService;
		this.qualifierLookupService = qualifierLookupService;
	}
	
	@LogReportExecutionTime
	public ExtremesReport buildReport(ExtremesRequestParameters requestParameters, String requestingUser) {
		ExtremesReport report = new ExtremesReport();
		ExtremesMinMax primaryOutput = new ExtremesMinMax();
		ExtremesMinMax upchainOutput = new ExtremesMinMax();
		ExtremesMinMax derviedOutput = new ExtremesMinMax();
		List<Qualifier> qualifiers = new ArrayList<>();
		
		
		// All TS Metadata
		Map<String, TimeSeriesDescription> timeSeriesDescriptions = 
				timeSeriesDescriptionListService.getTimeSeriesDescriptionList(new ArrayList<>(requestParameters.getTsIdSet()))
					.stream().collect(Collectors.toMap(t -> t.getUniqueId(), t -> t));
		
		// Primary TS Data
		TimeSeriesDescription primaryDescription = timeSeriesDescriptions.get(requestParameters.getPrimaryTimeseriesIdentifier());
		ZoneOffset primaryZoneOffset = TimeSeriesUtils.getZoneOffset(primaryDescription);
		Boolean primaryIsDaily = TimeSeriesUtils.isDailyTimeSeries(primaryDescription);
		
		TimeSeriesDataServiceResponse primaryData = timeSeriesDataService
				.get(primaryDescription.getUniqueId(), requestParameters,  primaryZoneOffset, primaryIsDaily, false, false, null);
		
		TimeSeriesMinMax primaryMinMax = null;

		if(primaryData != null && !primaryData.getPoints().isEmpty()) {
			primaryMinMax = minMaxBuilderService.findMinMaxPoints(primaryData.getPoints());
			primaryOutput.setMaxPoints(getExtremesPoints(primaryMinMax.getMaxPoints(), primaryIsDaily, primaryZoneOffset));
			primaryOutput.setMinPoints(getExtremesPoints(primaryMinMax.getMinPoints(), primaryIsDaily, primaryZoneOffset));
			primaryOutput.setQualifiers(getExtremesQualifiers(primaryData.getQualifiers(), primaryIsDaily, primaryZoneOffset));
			qualifiers.addAll(primaryData.getQualifiers());
		}

		// Upchain TS Data
		TimeSeriesDescription upchainDescription = timeSeriesDescriptions.get(requestParameters.getUpchainTimeseriesIdentifier());
		TimeSeriesDataServiceResponse upchainData = null;

		if(upchainDescription != null) {
			ZoneOffset upchainZoneOffset = TimeSeriesUtils.getZoneOffset(upchainDescription);
			Boolean upchainIsDaily = TimeSeriesUtils.isDailyTimeSeries(upchainDescription);
			upchainData = timeSeriesDataService
				.get(upchainDescription.getUniqueId(), requestParameters,  upchainZoneOffset, upchainIsDaily, false, false, null);
			if(upchainData != null && !upchainData.getPoints().isEmpty()) {
				TimeSeriesMinMax upchainMinMax = minMaxBuilderService.findMinMaxPoints(upchainData.getPoints());
				upchainOutput.setMaxPoints(getExtremesPoints(upchainMinMax.getMaxPoints(), upchainIsDaily, upchainZoneOffset));
				upchainOutput.setMinPoints(getExtremesPoints(upchainMinMax.getMinPoints(), upchainIsDaily, upchainZoneOffset));
				upchainOutput.setQualifiers(getExtremesQualifiers(upchainData.getQualifiers(), upchainIsDaily, upchainZoneOffset));
				qualifiers.addAll(upchainData.getQualifiers());

				// Find related data
				if(primaryMinMax != null && upchainMinMax != null) {
					TimeSeriesMinMax relatedUpchainMinMax = minMaxBuilderService.findMinMaxMatchingPoints(primaryMinMax, upchainData.getPoints());
					TimeSeriesMinMax relatedPrimaryMinMax = minMaxBuilderService.findMinMaxMatchingPoints(upchainMinMax, primaryData.getPoints());

					primaryOutput.setMaxRelatedPoints(
						getExtremesPoints(relatedUpchainMinMax.getMaxPoints(), upchainIsDaily, upchainZoneOffset),
						UPCHAIN_RELATED_KEY
					);
					primaryOutput.setMinRelatedPoints(
						getExtremesPoints(relatedUpchainMinMax.getMinPoints(), upchainIsDaily, upchainZoneOffset),
						UPCHAIN_RELATED_KEY
					);
					upchainOutput.setMaxRelatedPoints(
						getExtremesPoints(relatedPrimaryMinMax.getMaxPoints(), primaryIsDaily, primaryZoneOffset),
						PRIMARY_RELATED_KEY
					);
					upchainOutput.setMinRelatedPoints(
						getExtremesPoints(relatedPrimaryMinMax.getMinPoints(), primaryIsDaily, primaryZoneOffset),
						PRIMARY_RELATED_KEY
					);
				}
			}
		}
		
		// Dervied TS Data
		TimeSeriesDescription derviedDescription = timeSeriesDescriptions.get(requestParameters.getDerivedTimeseriesIdentifier());
		TimeSeriesDataServiceResponse derivedData = null;

		if(derviedDescription != null) {
			ZoneOffset derviedZoneOffset = TimeSeriesUtils.getZoneOffset(derviedDescription);
			derivedData = timeSeriesDataService
				.get(derviedDescription.getUniqueId(), requestParameters,  derviedZoneOffset, true, false, false, null);

			if(derivedData != null && !derivedData.getPoints().isEmpty()) {
				TimeSeriesMinMax derivedMinMax = minMaxBuilderService.findMinMaxPoints(derivedData.getPoints());
				derviedOutput.setMaxPoints(getExtremesPoints(derivedMinMax.getMaxPoints(), true, derviedZoneOffset));
				derviedOutput.setMinPoints(getExtremesPoints(derivedMinMax.getMinPoints(), true, derviedZoneOffset));
				derviedOutput.setQualifiers(getExtremesQualifiers(derivedData.getQualifiers(), true, derviedZoneOffset));
				qualifiers.addAll(derivedData.getQualifiers());
			}
		}

		// Output to report
		report.setPrimary(primaryOutput);
		report.setUpchain(upchainOutput);
		report.setDv(derviedOutput);

		//Report Metadata
		report.setReportMetadata(getReportMetadata(requestParameters,
			timeSeriesDescriptions,
			primaryDescription,
			requestingUser,
			qualifiers
		));

		return report;
	}

	@LogStep
	protected List<ExtremesPoint> getExtremesPoints(List<TimeSeriesPoint> points, Boolean isDaily, ZoneOffset zoneOffset) {
		if(points != null && !points.isEmpty()) {
			return points.stream().map(p -> new ExtremesPoint(p, isDaily, zoneOffset)).collect(Collectors.toList());
		}
		return new ArrayList<>();
	}
	
	@LogStep
	protected List<ExtremesQualifier> getExtremesQualifiers(List<Qualifier> quals, Boolean isDaily, ZoneOffset zoneOffset) {
		if(quals != null && !quals.isEmpty()) {
			return quals.stream().map(q -> new ExtremesQualifier(q, isDaily, zoneOffset)).collect(Collectors.toList());
		}
		return new ArrayList<>();
	}
	
	@LogStep
	protected ExtremesReportMetadata getReportMetadata(ExtremesRequestParameters requestParameters, 
			Map<String, TimeSeriesDescription> timeSeriesDescriptions,
			TimeSeriesDescription primarySeriesDescription,
			String requestingUser, 
			List<Qualifier> qualifierList) {
		ExtremesReportMetadata metadata = new ExtremesReportMetadata();
		metadata.setTitle(REPORT_TITLE);
		metadata.setRequestingUser(requestingUser);
		metadata.setRequestParameters(requestParameters);
		metadata.setStationId(primarySeriesDescription.getLocationIdentifier());
		metadata.setStationName(locationDescriptionListService.getByLocationIdentifier(primarySeriesDescription.getLocationIdentifier()).getName());
		metadata.setPrimaryParameter(primarySeriesDescription.getParameter());
		metadata.setPrimaryUnit(primarySeriesDescription.getUnit());
		metadata.setPrimaryLabel(primarySeriesDescription.getIdentifier());
		metadata.setTimezone(AqcuTimeUtils.getTimezone(primarySeriesDescription.getUtcOffset()));
		
		if (timeSeriesDescriptions.containsKey(requestParameters.getDerivedTimeseriesIdentifier())) {
			metadata.setDvLabel(
					timeSeriesDescriptions.get(requestParameters.getDerivedTimeseriesIdentifier()).getIdentifier());
			metadata.setDvComputation(
					timeSeriesDescriptions.get(requestParameters.getDerivedTimeseriesIdentifier()).getComputationIdentifier());
			metadata.setDvParameter(
					timeSeriesDescriptions.get(requestParameters.getDerivedTimeseriesIdentifier()).getParameter());
			metadata.setDvUnit(
					timeSeriesDescriptions.get(requestParameters.getDerivedTimeseriesIdentifier()).getUnit());
		}
		
		if (timeSeriesDescriptions.containsKey(requestParameters.getUpchainTimeseriesIdentifier())) {
			metadata.setUpchainLabel(
					timeSeriesDescriptions.get(requestParameters.getUpchainTimeseriesIdentifier()).getIdentifier());
			metadata.setUpchainParameter(
					timeSeriesDescriptions.get(requestParameters.getUpchainTimeseriesIdentifier()).getParameter());
			metadata.setUpchainUnit(
					timeSeriesDescriptions.get(requestParameters.getUpchainTimeseriesIdentifier()).getUnit());
		}
		
		if(qualifierList != null && !qualifierList.isEmpty()) {
			metadata.setQualifierMetadata(qualifierLookupService.getByQualifierList(qualifierList));
		}
		
		return metadata;
	}

}