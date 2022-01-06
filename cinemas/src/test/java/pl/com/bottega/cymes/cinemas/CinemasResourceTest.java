package pl.com.bottega.cymes.cinemas;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionTemplate;
import pl.com.bottega.cymes.cinemas.dataaccess.dao.PersistentCommandDao;
import pl.com.bottega.cymes.cinemas.events.CinemaSuspendedEvent;
import pl.com.bottega.cymes.cinemas.resources.request.CreateCinemaRequest;
import pl.com.bottega.cymes.cinemas.resources.request.SuspendRequest;
import pl.com.bottega.cymes.cinemas.services.dto.BasicCinemaInfoDto;

import java.time.Instant;

import static java.time.temporal.ChronoUnit.DAYS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.OK;

@IntegrationTest
public class CinemasResourceTest {

    private static final String REST_ROOT = "rest";

    @Autowired
    private CinemasClient cinemasClient;

    @Autowired
    private PersistentCommandDao persistentCommandDao;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private PublishedEventsAssertions publishedEventsAssertions;

    @Test
    public void createsAndReturnsCinemas() {
        // when
        var request = new CreateCinemaRequest();
        request.setCity("Warszawa");
        request.setName("Arkadia");
        var createCinemaResponse = cinemasClient.createCinema(request);
        var getCinemasResponse = cinemasClient.getCinemas();

        // then
        createCinemaResponse.expectStatus().isOk();
        getCinemasResponse
                .expectStatus().isOk()
                .expectBodyList(BasicCinemaInfoDto.class)
                .consumeWith(results ->
                        assertThat(results.getResponseBody())
                                .hasSize(1)
                                .anyMatch(cinema -> cinema.getCity().equals("Warszawa") && cinema.getName().equals("Arkadia"))
                );
    }

    @Test
    public void returnsBadRequestWhenCreateCinemaRequestIsInvalid() {
        // when
        var request = new CreateCinemaRequest();
        request.setCity(null);
        request.setName("Arkadia");
        var createCinemaResponse = cinemasClient.createCinema(request);

        // then
        createCinemaResponse.expectStatus().isBadRequest();
    }

    @Test
    public void storesCreateCinemaCommand() {
        //when
        var request = new CreateCinemaRequest();
        request.setCity("Warszawa");
        request.setName("Arkadia");
        cinemasClient.createCinema(request);

        // then
        transactionTemplate.execute((callback) -> {
            var persistentCommands = persistentCommandDao.findAll();
            assertThat(persistentCommands).anyMatch(cmd ->
                    cmd.getType().equals("CreateCinemaCommand") &&
                            cmd.getContent().contains("Warszawa") &&
                            cmd.getContent().contains(request.getName())
            );
            return null;
        });
    }

    @Test
    public void doesNotAllowToDuplicateCinemas() {
        // given
        var request = new CreateCinemaRequest();
        request.setCity("Warszawa");
        request.setName("Arkadia");

        // when
        var firstResponse = cinemasClient.createCinema(request);
        var secondResponse = cinemasClient.createCinema(request);

        // then
        firstResponse.expectStatus().isOk();
        secondResponse.expectStatus().isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    public void publishesEventWhenCinemaIsSuspended() {
        // given
        var request = new CreateCinemaRequest();
        request.setCity("Warszawa");
        request.setName("Arkadia");
        cinemasClient.createCinema(request);
        var cinema = cinemasClient.getCinemas().expectBodyList(BasicCinemaInfoDto.class)
            .returnResult().getResponseBody().get(0);

        // when
        var suspendRequest = new SuspendRequest();
        suspendRequest.setFrom(Instant.now().plus(1, DAYS));
        suspendRequest.setUntil(Instant.now().plus(2, DAYS));
        var suspendResp = cinemasClient.suspendCinema(cinema.getId(), suspendRequest);

        // then
        suspendResp.expectStatus().isEqualTo(OK);
        publishedEventsAssertions.awaitEventPublished(
            new CinemaSuspendedEvent(cinema.getId(), suspendRequest.getFrom(), suspendRequest.getUntil())
        );
    }
}