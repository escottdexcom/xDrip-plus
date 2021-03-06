package com.eveningoutpost.dexdrip.calibrations;

import android.util.Log;

import com.eveningoutpost.dexdrip.Home;
import com.eveningoutpost.dexdrip.Models.Calibration;
import com.eveningoutpost.dexdrip.Models.Forecast;
import com.eveningoutpost.dexdrip.Models.Forecast.PolyTrendLine;
import com.eveningoutpost.dexdrip.Models.Forecast.TrendLine;
import com.eveningoutpost.dexdrip.Models.JoH;
import com.eveningoutpost.dexdrip.utils.DexCollectionType;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by jamorham on 04/10/2016.
 * <p>
 * Maintained by jamorham
 * <p>
 * If you would like to modify a calibration plugin,
 * please create a new one and make the modifications there
 */

public class Datricsae extends CalibrationAbstract {

    private static final String TAG = "Datricsae";
    private static final double MINIMUM_SLOPE = 0.5;
    private static final double MAXIMUM_SLOPE = 1.5;
    private static final int CALIBRATIONS_TO_USE = 8;
    private static final int OPTIMIZE_OUTLIERS_CALIBRATION_MINIMUM = 4;
    private static final int FALLBACK_TO_ORIGINAL_CALIBRATIONS_MINIMUM = 4;
    private static final boolean d = false;


    @Override
    public String getAlgorithmName() {
        return TAG;
    }

    @Override
    public String getAlgorithmDescription() {
        return "pronounced: da-trix-ee - place holder only - do not use";
    }

    @Override
    public CalibrationData getCalibrationData() {

        CalibrationData cd = loadDataFromCache(TAG);
        if (cd == null) {

            // first is most recent
            final List<Calibration> calibrations = Calibration.latestValid(CALIBRATIONS_TO_USE);
            if ((calibrations == null) || (calibrations.size() == 0)) return null;
            // have we got enough data to have a go
            if (calibrations.size() < FALLBACK_TO_ORIGINAL_CALIBRATIONS_MINIMUM) {
                // just use whatever xDrip original would have come up with at this point
                cd = new CalibrationData(calibrations.get(0).slope, calibrations.get(0).intercept);
            } else {
                // TODO sanity checks
                final TrendLine bg_to_raw = new Forecast.PolyTrendLine(1);

                final List<Double> raws = new ArrayList<>();
                final List<Double> bgs = new ArrayList<>();
                final boolean adjust_raw = !DexCollectionType.hasLibre();

                final int ccount = calibrations.size();
                for (Calibration calibration : calibrations) {
                    // sanity check?
                    // weighting!
                    final double raw = adjust_raw ? calibration.adjusted_raw_value : calibration.raw_value;
                    Log.d(TAG, "Calibration: " + JoH.qs(raw,4) + " -> " + JoH.qs(calibration.bg,4)+"  @ "+JoH.dateTimeText(calibration.raw_timestamp));
                    raws.add(raw);
                    bgs.add(calibration.bg);
                }

                bg_to_raw.setValues(PolyTrendLine.toPrimitiveFromList(bgs), PolyTrendLine.toPrimitiveFromList(raws));
                final double all_varience = bg_to_raw.errorVarience();
                Log.d(TAG, "Error Variance All: " + all_varience);

                // TODO CHECK SLOPE IN RANGE HERE

                final List<Double> all_bgs_set = new ArrayList<>();
                final List<Double> all_raw_set = new ArrayList<>();
                all_bgs_set.addAll(bgs);
                all_raw_set.addAll(raws);


                List<Double> lowest_bgs_set = new ArrayList<>();
                List<Double> lowest_raws_set = new ArrayList<>();
                lowest_bgs_set.addAll(all_bgs_set);
                lowest_raws_set.addAll(all_raw_set);

                double lowest_variance = all_varience;

                // TODO single pass at the mo must be carefully handled
                if (ccount>= OPTIMIZE_OUTLIERS_CALIBRATION_MINIMUM)
                {
                    for (int i=0;i<ccount;i++)
                    {
                        // reset
                        bgs.clear();
                        bgs.addAll(all_bgs_set);
                        raws.clear();
                        raws.addAll(all_raw_set);

                        bgs.remove(i);
                        raws.remove(i);
                        bg_to_raw.setValues(PolyTrendLine.toPrimitiveFromList(bgs), PolyTrendLine.toPrimitiveFromList(raws));
                        final double this_variance = bg_to_raw.errorVarience();
                        Log.d(TAG, "Error Variance drop: "+i+" = "+JoH.qs(this_variance,3));
                        if (this_variance<lowest_variance)
                        {

                            final double intercept = bg_to_raw.predict(0);
                            final double one = bg_to_raw.predict(1);
                            final double slope = one - intercept;

                            Log.d(TAG,"Removing outlier: "+i+" Reduces varience to: "+JoH.qs(this_variance,3)+" Slope: "+JoH.qs(slope,3)+" "+slope_in_range(slope));

                            if (slope_in_range(slope)) {
                                lowest_variance = this_variance;
                                lowest_bgs_set.clear();
                                lowest_bgs_set.addAll(bgs);
                                lowest_raws_set.clear();
                                lowest_raws_set.addAll(raws);
                            }
                        }
                    }
                }

                bg_to_raw.setValues(PolyTrendLine.toPrimitiveFromList(lowest_bgs_set), PolyTrendLine.toPrimitiveFromList(lowest_raws_set));

                final double intercept = bg_to_raw.predict(0);
                Log.d(TAG, "Intercept: " + intercept);
                final double one = bg_to_raw.predict(1);
                Log.d(TAG, "One: " + one);
                final double slope = one - intercept;
                Log.d(TAG, "Slope: " + slope);
                cd = new CalibrationData(slope, intercept);
            }
        } else {
            Log.d(TAG,"Returning cached calibration data object");
        }
        //saveDataToCache(TAG, cd); // Save cached data when not in development
        return cd; // null if invalid
    }

    private static boolean slope_in_range(double slope) {
        return (slope >= MINIMUM_SLOPE) && (slope <= MAXIMUM_SLOPE);
    }


    @Override
    public boolean invalidateCache() {
        return saveDataToCache(TAG, null);
    }

    // this means we invalidate the cache close to a calibration if our alg
    // makes use of this updated data as the xDrip original algorithm does
    @Override
    public boolean newCloseSensorData() {
        return invalidateCache();
    }
}
