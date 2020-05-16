package pl.com.bottega.cymes.showscheduler.domain;

import lombok.AllArgsConstructor;
import lombok.NonNull;

@AllArgsConstructor
class DefaultScheduleShowHandler implements ScheduleShowHandler {

    @NonNull
    private final MovieCatalog movieCatalog;

    @NonNull
    private final ShowRepository showRepository;

    @NonNull
    private final SuspensionChecker suspensionChecker;

    @NonNull
    private final OperationLocker operationLocker;

    @NonNull
    private final ShowSchedulerConfiguration configuration;

    @Override
    public void handle(ScheduleShowCommand command) {
        var movie = movieCatalog.get(command.getMovieId());
        var show = new Show(command, movie, configuration);
        if(suspensionChecker.isSuspended(command.getCinemaId(), command.getCinemaHallId())) {
            throw new CinemaHallNotAvailableException();
        }
        operationLocker.lock(command.getCinemaId().toString(), command.getCinemaHallId().toString(), () -> {
            if(showRepository.anyShowsCollidingWith(show)) {
                throw new CinemaHallNotAvailableException();
            }
            showRepository.save(show);
            return null;
        });
    }
}