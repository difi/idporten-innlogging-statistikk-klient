package no.difi.statistikk.fetch;

import no.difi.statistics.ingest.client.IngestClient;
import no.difi.statistics.ingest.client.model.TimeSeriesDefinition;
import no.difi.statistics.ingest.client.model.TimeSeriesPoint;
import no.difi.statistikk.domain.IdportenLoginField;
import no.difi.statistikk.mapper.IdportenLoginMapper;

import no.difi.statistikk.service.IdportenLoginFetch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static java.util.Arrays.asList;
import static no.difi.statistics.ingest.client.model.MeasurementDistance.hours;
import static no.difi.statistics.ingest.client.model.TimeSeriesDefinition.timeSeriesDefinition;

public class DataTransfer {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final IdportenLoginFetch fetch;
    private final IdportenLoginMapper idportenLoginMapper;
    private final IngestClient ingestClient;

    private static final TimeSeriesDefinition timeSeriesDefinition = timeSeriesDefinition()
            .name("idporten-innlogging")
            .distance(hours);

    public DataTransfer(IdportenLoginFetch fetch, IdportenLoginMapper idportenLoginMapper, IngestClient ingestClient) {
        this.fetch = fetch;
        this.idportenLoginMapper = idportenLoginMapper;
        this.ingestClient = ingestClient;
    }

    public void transfer(ZonedDateTime from, ZonedDateTime to) {

        while (missingDataForNextHour(to) && from.isBefore(to)) {
            to = to.minusHours(1);
        }

        if (!missingDataForNextHour(to)) {
            logger.info("Transfering data for {}", DateTimeFormatter.ofPattern("yyyy-MM-dd - hh:00").format(from) + " to " + DateTimeFormatter.ofPattern("yyyy-MM-dd - hh:00").format(to));
            while (from.isBefore(to) || from.isEqual(to)) {

                final List<IdportenLoginField> r1Report = asList(fetch.perform(from));
                if (r1Report.isEmpty()) throw new FetchError("R1 report is empty");
                if (r1Report.get(0).getValues().size() == 0) {
                    logger.warn("Report from R1 contains no data for {}, skip transfer to statistics", from);
                } else {
                    List<TimeSeriesPoint> timeSeriesPoints = idportenLoginMapper.mapMeasurements(r1Report, from);
                    ingestClient.ingest(timeSeriesDefinition, timeSeriesPoints);
                }
                from = from.plusHours(1);
            }
        }
    }

    private boolean missingDataForNextHour(ZonedDateTime timestamp) {
        return asList(fetch.perform(timestamp.plusHours(1))).get(0).getValues().size() == 0;
    }
}