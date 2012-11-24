/*
 * AndFHEM - Open Source Android application to control a FHEM home automation
 * server.
 *
 * Copyright (c) 2011, Matthias Klass or third-party contributors as
 * indicated by the @author tags or express copyright attribution
 * statements applied by the authors.  All third-party contributions are
 * distributed under license by Red Hat Inc.
 *
 * This copyrighted material is made available to anyone wishing to use, modify,
 * copy, or redistribute it subject to the terms and conditions of the GNU GENERAL PUBLIC LICENSE, as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU GENERAL PUBLIC LICENSE
 * for more details.
 *
 * You should have received a copy of the GNU GENERAL PUBLIC LICENSE
 * along with this distribution; if not, write to:
 *   Free Software Foundation, Inc.
 *   51 Franklin Street, Fifth Floor
 *   Boston, MA  02110-1301  USA
 */

package li.klass.fhem.adapter.devices;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.view.LayoutInflater;
import android.widget.DatePicker;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import li.klass.fhem.R;
import li.klass.fhem.adapter.devices.core.FieldNameAddedToDetailListener;
import li.klass.fhem.adapter.devices.core.GenericDeviceAdapter;
import li.klass.fhem.adapter.devices.genericui.DeviceDetailViewAction;
import li.klass.fhem.adapter.devices.genericui.SeekBarActionRowFullWidthAndButton;
import li.klass.fhem.adapter.devices.genericui.SpinnerActionRow;
import li.klass.fhem.adapter.devices.genericui.TemperatureChangeTableRow;
import li.klass.fhem.constants.Actions;
import li.klass.fhem.constants.BundleExtraKeys;
import li.klass.fhem.domain.FHTDevice;
import li.klass.fhem.domain.fht.FHTMode;
import li.klass.fhem.fragments.FHTTimetableControlListFragment;
import li.klass.fhem.util.DatePickerUtil;
import li.klass.fhem.util.DialogUtil;

import java.util.Calendar;

import static li.klass.fhem.domain.FHTDevice.MAXIMUM_TEMPERATURE;
import static li.klass.fhem.domain.FHTDevice.MINIMUM_TEMPERATURE;

public class FHTAdapter extends GenericDeviceAdapter<FHTDevice> {

    public FHTAdapter() {
        super(FHTDevice.class);
    }

    @Override
    protected void afterPropertiesSet() {
        registerFieldListener("desiredTemp", new FieldNameAddedToDetailListener<FHTDevice>() {
            @Override
            public void onFieldNameAdded(Context context, TableLayout tableLayout, String field, FHTDevice device, TableRow fieldTableRow) {
                tableLayout.addView(new TemperatureChangeTableRow<FHTDevice>(context, device.getDesiredTemp(), fieldTableRow,
                        Actions.DEVICE_SET_DESIRED_TEMPERATURE, R.string.desiredTemperature, MINIMUM_TEMPERATURE, MAXIMUM_TEMPERATURE)
                        .createRow(inflater, device));
            }
        });
        registerFieldListener("dayTemperature", new FieldNameAddedToDetailListener<FHTDevice>() {
            @Override
            public void onFieldNameAdded(Context context, TableLayout tableLayout, String field, FHTDevice device, TableRow fieldTableRow) {
                tableLayout.addView(new TemperatureChangeTableRow<FHTDevice>(context, device.getDayTemperature(), fieldTableRow,
                        Actions.DEVICE_SET_DAY_TEMPERATURE, R.string.dayTemperature, MINIMUM_TEMPERATURE, MAXIMUM_TEMPERATURE)
                        .createRow(inflater, device));
            }
        });
        registerFieldListener("nightTemperature", new FieldNameAddedToDetailListener<FHTDevice>() {
            @Override
            public void onFieldNameAdded(Context context, TableLayout tableLayout, String field, FHTDevice device, TableRow fieldTableRow) {
                tableLayout.addView(new TemperatureChangeTableRow<FHTDevice>(context, device.getNightTemperature(), fieldTableRow,
                        Actions.DEVICE_SET_NIGHT_TEMPERATURE, R.string.nightTemperature, MINIMUM_TEMPERATURE, MAXIMUM_TEMPERATURE)
                        .createRow(inflater, device));
            }
        });
        registerFieldListener("windowOpenTemp", new FieldNameAddedToDetailListener<FHTDevice>() {
            @Override
            public void onFieldNameAdded(Context context, TableLayout tableLayout, String field, FHTDevice device, TableRow fieldTableRow) {
                tableLayout.addView(new TemperatureChangeTableRow<FHTDevice>(context, device.getWindowOpenTemp(), fieldTableRow,
                        Actions.DEVICE_SET_WINDOW_OPEN_TEMPERATURE, R.string.windowOpenTemp, MINIMUM_TEMPERATURE, MAXIMUM_TEMPERATURE)
                        .createRow(inflater, device));
            }
        });
        registerFieldListener("actuator", new FieldNameAddedToDetailListener<FHTDevice>() {
            @Override
            public void onFieldNameAdded(Context context, TableLayout tableLayout, String field, FHTDevice device, TableRow fieldTableRow) {
                FHTMode mode = device.getMode();
                int selected = mode != null ? FHTMode.positionOf(mode) : FHTMode.positionOf(FHTMode.UNKNOWN);
                tableLayout.addView(new SpinnerActionRow<FHTDevice>(context, R.string.mode, R.string.setMode, FHTMode.toStringList(), selected) {

                    @Override
                    public void onItemSelected(Context context, FHTDevice device, String item) {
                        FHTMode mode = FHTMode.valueOf(item);
                        setMode(context, device, mode, this);
                    }
                }.createRow(device));
            }
        });

        detailActions.add(new DeviceDetailViewAction<FHTDevice>(R.string.timetable) {
            @Override
            public void onButtonClick(Context context, FHTDevice device) {
                Intent intent = new Intent(Actions.SHOW_FRAGMENT);
                intent.putExtra(BundleExtraKeys.FRAGMENT_NAME, FHTTimetableControlListFragment.class.getName());
                intent.putExtra(BundleExtraKeys.DEVICE_NAME, device.getName());
                context.sendBroadcast(intent);
            }
        });

        detailActions.add(new DeviceDetailViewAction<FHTDevice>(R.string.requestRefresh) {
            @Override
            public void onButtonClick(Context context, FHTDevice device) {
                Intent intent = new Intent(Actions.DEVICE_REFRESH_VALUES);
                intent.putExtra(BundleExtraKeys.DEVICE_NAME, device.getName());
                context.startService(intent);
            }
        });
    }

    private void setMode(final Context context, FHTDevice device, FHTMode mode, final SpinnerActionRow<FHTDevice> spinnerActionRow) {
        final Intent intent = new Intent(Actions.DEVICE_SET_MODE);
        intent.putExtra(BundleExtraKeys.DEVICE_NAME, device.getName());
        intent.putExtra(BundleExtraKeys.DEVICE_MODE, mode);

        LayoutInflater inflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        switch (mode) {
            case HOLIDAY:

                handleHolidayMode(context, device, spinnerActionRow, intent, inflater);

                break;

            case HOLIDAY_SHORT:

                handleHolidayShortMode(context, device, spinnerActionRow, intent, inflater);

                break;
            default:
                context.startService(intent);
        }
    }

    private void handleHolidayShortMode(final Context context, FHTDevice device, final SpinnerActionRow<FHTDevice> spinnerActionRow, final Intent intent, LayoutInflater inflater) {
        final AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(context);

        TableLayout contentView = (TableLayout) inflater.inflate(R.layout.fht_holiday_short_dialog, null);
        dialogBuilder.setView(contentView);

        TableRow temperatureUpdateRow = (TableRow) contentView.findViewById(R.id.updateTemperatureRow);
        TableRow durationUpdateRow = (TableRow) contentView.findViewById(R.id.updateDurationRow);

        final PartyModeTimeSelectionTableRow durationRow = new PartyModeTimeSelectionTableRow(context, durationUpdateRow);
        contentView.addView(durationRow.createRow(inflater, device), 1);

        final TemperatureChangeTableRow<FHTDevice> temperatureChangeTableRow =
                new TemperatureChangeTableRow<FHTDevice>(context, FHTDevice.MINIMUM_TEMPERATURE, temperatureUpdateRow,
                        FHTDevice.MINIMUM_TEMPERATURE, FHTDevice.MAXIMUM_TEMPERATURE);
        contentView.addView(temperatureChangeTableRow.createRow(inflater, device));


        dialogBuilder.setNegativeButton(R.string.cancelButton, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                spinnerActionRow.revertSelection();
                dialogInterface.dismiss();
            }
        });

        dialogBuilder.setPositiveButton(R.string.okButton, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int item) {
                intent.putExtra(BundleExtraKeys.DEVICE_TEMPERATURE, temperatureChangeTableRow.getTemperature());
                intent.putExtra(BundleExtraKeys.DEVICE_HOLIDAY1, durationRow.getTargetDayOfMonth());
                intent.putExtra(BundleExtraKeys.DEVICE_HOLIDAY2, durationRow.getPartyModeTimeSpanInMinutes());

                context.startService(intent);

                dialogInterface.dismiss();
            }
        });
        dialogBuilder.show();
    }

    private void handleHolidayMode(final Context context, FHTDevice device, final SpinnerActionRow<FHTDevice> spinnerActionRow, final Intent intent, LayoutInflater inflater) {
        final AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(context);

        TableLayout contentView = (TableLayout) inflater.inflate(R.layout.fht_holiday_dialog, null);

        final DatePicker datePicker = (DatePicker) contentView.findViewById(R.id.datePicker);
        DatePickerUtil.hideYearField(datePicker);

        TableRow temperatureUpdateRow = (TableRow) contentView.findViewById(R.id.updateRow);

        final TemperatureChangeTableRow<FHTDevice> temperatureChangeTableRow =
                new TemperatureChangeTableRow<FHTDevice>(context, FHTDevice.MINIMUM_TEMPERATURE, temperatureUpdateRow,
                FHTDevice.MINIMUM_TEMPERATURE, FHTDevice.MAXIMUM_TEMPERATURE);
        contentView.addView(temperatureChangeTableRow.createRow(inflater, device));

        dialogBuilder.setView(contentView);

        dialogBuilder.setNegativeButton(R.string.cancelButton, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                spinnerActionRow.revertSelection();
                dialogInterface.dismiss();
            }
        });

        dialogBuilder.setPositiveButton(R.string.okButton, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int item) {
                intent.putExtra(BundleExtraKeys.DEVICE_TEMPERATURE, temperatureChangeTableRow.getTemperature());
                intent.putExtra(BundleExtraKeys.DEVICE_HOLIDAY1, datePicker.getDayOfMonth());
                intent.putExtra(BundleExtraKeys.DEVICE_HOLIDAY2, datePicker.getMonth() + 1);

                context.startService(intent);

                dialogInterface.dismiss();
            }
        });

        dialogBuilder.show();
    }

    private class PartyModeTimeSelectionTableRow extends SeekBarActionRowFullWidthAndButton<FHTDevice> {
        private int currentValue = 0;
        private final TextView updateView;

        public PartyModeTimeSelectionTableRow(Context context, TableRow updateTableRow) {
            super(context, 0, 144);
            updateView = (TextView) updateTableRow.findViewById(R.id.value);
        }

        @Override
        public void onButtonSetValue(FHTDevice device, int value) {
            if (value > 1440) {
                DialogUtil.showAlertDialog(context, -1, R.string.invalidInput);
                return;
            }
            setValue(value / 10);
        }

        @Override
        public void onProgressChanged(Context context, FHTDevice device, int progress) {
            super.onProgressChanged(context, device, progress);
            setValue(progress);
        }

        private void setValue(int progress) {
            currentValue = progress;
            updateView.setText("" + currentValue * 10);
        }

        @Override
        public void onStopTrackingTouch(Context context, FHTDevice device, int progress) {
        }

        public int getPartyModeTimeSpanInMinutes() {
            return currentValue * 10;
        }

        public int getTargetDayOfMonth() {
            Calendar calendar = Calendar.getInstance();
            calendar.add(Calendar.MINUTE, getPartyModeTimeSpanInMinutes());

            return calendar.get(Calendar.DAY_OF_MONTH);
        }

        @Override
        protected boolean showButton() {
            return false;
        }


    }

}
