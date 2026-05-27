package be.mygod.datasimtile;

import android.app.job.JobParameters;
import android.app.job.JobService;

public final class TileRefreshJobService extends JobService {
    @Override
    public boolean onStartJob(JobParameters params) {
        TileRefreshScheduler.onJob(this);
        return false;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return false;
    }
}
