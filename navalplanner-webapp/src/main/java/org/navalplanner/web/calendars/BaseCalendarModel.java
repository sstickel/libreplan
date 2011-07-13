/*
 * This file is part of NavalPlan
 *
 * Copyright (C) 2009-2010 Fundación para o Fomento da Calidade Industrial e
 *                         Desenvolvemento Tecnolóxico de Galicia
 * Copyright (C) 2010-2011 Igalia, S.L.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.navalplanner.web.calendars;

import static org.navalplanner.web.I18nHelper._;

import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang.Validate;
import org.hibernate.validator.InvalidValue;
import org.joda.time.LocalDate;
import org.navalplanner.business.calendars.daos.IBaseCalendarDAO;
import org.navalplanner.business.calendars.daos.ICalendarExceptionTypeDAO;
import org.navalplanner.business.calendars.entities.BaseCalendar;
import org.navalplanner.business.calendars.entities.CalendarAvailability;
import org.navalplanner.business.calendars.entities.CalendarData;
import org.navalplanner.business.calendars.entities.CalendarException;
import org.navalplanner.business.calendars.entities.CalendarExceptionType;
import org.navalplanner.business.calendars.entities.Capacity;
import org.navalplanner.business.calendars.entities.ResourceCalendar;
import org.navalplanner.business.calendars.entities.BaseCalendar.DayType;
import org.navalplanner.business.calendars.entities.CalendarData.Days;
import org.navalplanner.business.common.IntegrationEntity;
import org.navalplanner.business.common.daos.IConfigurationDAO;
import org.navalplanner.business.common.entities.Configuration;
import org.navalplanner.business.common.entities.EntityNameEnum;
import org.navalplanner.business.common.exceptions.InstanceNotFoundException;
import org.navalplanner.business.common.exceptions.ValidationException;
import org.navalplanner.business.workingday.EffortDuration;
import org.navalplanner.business.workingday.IntraDayDate.PartialDay;
import org.navalplanner.web.common.IntegrationEntityModel;
import org.navalplanner.web.common.concurrentdetection.OnConcurrentModification;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Model for UI operations related to {@link BaseCalendar}.
 * @author Manuel Rego Casasnovas <mrego@igalia.com>
 * @author Diego Pino Garcia <dpino@igalia.com>
 */
@Service
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
@Qualifier("main")
@OnConcurrentModification(goToPage = "/calendars/calendars.zul")
public class BaseCalendarModel extends IntegrationEntityModel implements
        IBaseCalendarModel {

    /**
     * Conversation state
     */
    protected BaseCalendar baseCalendar;

    private LocalDate selectedDate = new LocalDate();

    protected boolean editing = false;

    @Autowired
    private IBaseCalendarDAO baseCalendarDAO;

    @Autowired
    private IConfigurationDAO configurationDAO;

    @Autowired
    private ICalendarExceptionTypeDAO calendarExceptionTypeDAO;

    /*
     * Non conversational steps
     */

    @Override
    @Transactional(readOnly = true)
    public List<BaseCalendar> getBaseCalendars() {
        List<BaseCalendar> baseCalendars = baseCalendarDAO.getBaseCalendars();
        for (BaseCalendar each : baseCalendars) {
            forceLoad(each);
        }
        return baseCalendars;
    }

    @Override
    @Transactional(readOnly = true)
    public List<BaseCalendar> getSortedBaseCalendars(
            List<BaseCalendar> baseCalendars) {
        return BaseCalendar.sortByName(baseCalendars);
    }

    /*
     * Initial conversation steps
     */

    @Override
    @Transactional(readOnly = true)
    public void initCreate() {
        editing = false;

        boolean codeGenerated = (configurationDAO.getConfiguration() != null) ? configurationDAO
                .getConfiguration().getGenerateCodeForBaseCalendars()
                : false;

        this.baseCalendar = BaseCalendar.createBasicCalendar();

        if (codeGenerated) {
            setDefaultCode();
        }
        baseCalendar.setCodeAutogenerated(codeGenerated);
    }

    @Override
    @Transactional(readOnly = true)
    public void initEdit(BaseCalendar baseCalendar) {
        editing = true;
        Validate.notNull(baseCalendar);

        this.baseCalendar = getFromDB(baseCalendar);
        forceLoad(this.baseCalendar);
        initOldCodes();
    }

    @Override
    @Transactional(readOnly = true)
    public void initCreateDerived(BaseCalendar baseCalendar) {
        editing = false;
        Validate.notNull(baseCalendar);

        this.baseCalendar = getFromDB(baseCalendar).newDerivedCalendar();
        forceLoad(this.baseCalendar);
        this.baseCalendar.setCode("");

        boolean codeGenerated = (configurationDAO.getConfiguration() != null) ? configurationDAO
                .getConfiguration().getGenerateCodeForBaseCalendars()
                : false;

        if (codeGenerated) {
            setDefaultCode();
        }
        this.baseCalendar.setCodeAutogenerated(codeGenerated);
    }

    @Override
    @Transactional(readOnly = true)
    public void initCreateCopy(BaseCalendar baseCalendar) {
        editing = false;
        Validate.notNull(baseCalendar);

        this.baseCalendar = getFromDB(baseCalendar).newCopy();
        forceLoad(this.baseCalendar);
        this.baseCalendar.setCode("");

        if (this.baseCalendar.isCodeAutogenerated()) {
            setDefaultCode();
        }
    }

    @Override
    public void initRemove(BaseCalendar baseCalendar) {
        this.baseCalendar = baseCalendar;
    }

    protected void forceLoad(BaseCalendar baseCalendar) {
        forceLoadBaseCalendar(baseCalendar);
        forceLoadExceptionTypes();
    }

    public static void forceLoadBaseCalendar(BaseCalendar baseCalendar) {
        for (CalendarData calendarData : baseCalendar.getCalendarDataVersions()) {
            calendarData.getHoursPerDay().size();
            if (calendarData.getParent() != null) {
                forceLoadBaseCalendar(calendarData.getParent());
            }
        }
        loadingExceptionsWithTheirTypes(baseCalendar);
        baseCalendar.getCalendarAvailabilities().size();
    }

    private static void loadingExceptionsWithTheirTypes(
            BaseCalendar baseCalendar) {
        Set<CalendarException> exceptions = baseCalendar.getExceptions();
        for (CalendarException each : exceptions) {
            each.getType().getName();
        }
    }

    private void forceLoadExceptionTypes() {
        for (CalendarExceptionType calendarExceptionType : getCalendarExceptionTypes()) {
            calendarExceptionType.getName();
        }
    }

    @Transactional(readOnly = true)
    private BaseCalendar getFromDB(Long id) {
        try {
            BaseCalendar result = baseCalendarDAO.find(id);
            return result;
        } catch (InstanceNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    protected BaseCalendar getFromDB(BaseCalendar baseCalendar) {
        return getFromDB(baseCalendar.getId());
    }

    /*
     * Intermediate conversation steps
     */
    @Override
    public BaseCalendar getBaseCalendar() {
        return baseCalendar;
    }

    @Override
    @Transactional(readOnly = true)
    public List<BaseCalendar> getPossibleParentCalendars() {
        List<BaseCalendar> baseCalendars = getBaseCalendars();

        if (getBaseCalendar() != null) {
            for (BaseCalendar calendar : baseCalendars) {
                if (calendar.getId().equals(getBaseCalendar().getId())) {
                    baseCalendars.remove(calendar);
                    break;
                }
            }
        }

        return baseCalendars;
    }

    @Override
    public boolean isEditing() {
        return this.editing;
    }

    @Override
    public void setSelectedDay(LocalDate date) {
        this.selectedDate = date != null ? date : new LocalDate();
    }

    @Override
    public LocalDate getSelectedDay() {
        return selectedDate;
    }

    @Override
    public EffortDuration getWorkableTime() {
        if (getBaseCalendar() == null) {
            return null;
        }
        return getBaseCalendar().getCapacityOn(
                PartialDay.wholeDay(selectedDate));
    }

    @Override
    public Capacity getWorkableCapacity() {
        if (getBaseCalendar() == null) {
            return null;
        }
        return getBaseCalendar().getCapacityWithOvertime(selectedDate);
    }

    @Override
    public DayType getTypeOfDay() {
        if (getBaseCalendar() == null) {
            return null;
        }

        return getBaseCalendar().getType(selectedDate);
    }

    @Override
    public DayType getTypeOfDay(LocalDate date) {
        if (getBaseCalendar() == null) {
            return null;
        }

        return getBaseCalendar().getType(date);
    }

    @Override
    public void createException(CalendarExceptionType type,
            LocalDate startDate, LocalDate endDate, Capacity capacity) {
        for (LocalDate date = startDate; date.compareTo(endDate) <= 0; date = date
                .plusDays(1)) {
            if (getTypeOfDay(date).equals(DayType.OWN_EXCEPTION)) {
                getBaseCalendar().updateExceptionDay(date, capacity, type);
            } else {
                CalendarException day = CalendarException.create("", date,
                        capacity, type);
                getBaseCalendar().addExceptionDay(day);
            }
        }
    }

    @Override
    public Capacity getCapacityAt(Days day) {
        if (getBaseCalendar() == null) {
            return Capacity.zero();
        }
        return getBaseCalendar().getCapacityConsideringCalendarDatasOn(
                selectedDate, day);
    }

    @Override
    public Boolean isDefault(Days day) {
        if (getBaseCalendar() == null) {
            return false;
        }

        return getBaseCalendar().isDefault(day, selectedDate);
    }

    @Override
    public void unsetDefault(Days day) {
        if (getBaseCalendar() != null) {
            Capacity previousCapacity = getBaseCalendar()
                    .getCapacityConsideringCalendarDatasOn(selectedDate, day);
            getBaseCalendar()
                    .setCapacityAt(day, previousCapacity, selectedDate);
        }
    }

    @Override
    public void setDefault(Days day) {
        if (getBaseCalendar() != null) {
            getBaseCalendar().setDefault(day, selectedDate);
        }
    }

    @Override
    public void setCapacityAt(Days day, Capacity value) {
        if (getBaseCalendar() != null) {
            getBaseCalendar().setCapacityAt(day, value, selectedDate);
        }
    }

    @Override
    public boolean isExceptional() {
        if (getBaseCalendar() == null) {
            return false;
        }

        CalendarException day = getBaseCalendar().getOwnExceptionDay(selectedDate);
        return (day != null);
    }

    @Override
    public void removeException() {
        getBaseCalendar().removeExceptionDay(selectedDate);
    }

    @Override
    public boolean isDerived() {
        if (getBaseCalendar() == null) {
            return false;
        }

        return getBaseCalendar().isDerived(selectedDate);
    }

    @Override
    public BaseCalendar getParent() {
        if (getBaseCalendar() == null) {
            return null;
        }

        return getBaseCalendar().getParent(selectedDate);
    }

    @Override
    public BaseCalendar getCurrentParent() {
        if (getBaseCalendar() == null) {
            return null;
        }
        CalendarData version = getCurrentVersion();
        return version != null ? version.getParent() : null;
    }

    @Override
    public Date getCurrentExpiringDate() {
        CalendarData calendarData = getCurrentVersion();
        if (calendarData != null) {
            LocalDate startDate = calendarData.getExpiringDate();
            return startDate != null ? startDate.minusDays(1)
                    .toDateTimeAtStartOfDay()
                    .toDate() : null;
        }
        return null;
    }

    @Override
    public Date getCurrentStartDate() {
        CalendarData calendarData = getCurrentVersion();
        if (calendarData != null) {
            LocalDate startDate = getValidFrom(calendarData);
            return startDate != null ? startDate.toDateTimeAtStartOfDay()
                    .toDate() : null;
        }
        return null;
    }

    public CalendarData getCurrentVersion() {
        return getBaseCalendar().getCalendarData(
                LocalDate.fromDateFields(new Date()));
    }

    @Override
    @Transactional(readOnly = true)
    public void setParent(BaseCalendar parent) {
        try {
            parent = baseCalendarDAO.find(parent.getId());
        } catch (InstanceNotFoundException e) {
            throw new RuntimeException(e);
        }
        forceLoad(parent);

        if (getBaseCalendar() != null) {
            getBaseCalendar().setParent(parent, selectedDate);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isParent(BaseCalendar calendar) {
        if (calendar == null) {
            return false;
        }
        return !baseCalendarDAO.findByParent(calendar).isEmpty();
    }

    @Override
    public LocalDate getDateValidFrom() {
        if (getBaseCalendar() != null) {
            LocalDate validFromDate = getBaseCalendar().getValidFrom(
                    selectedDate);
            return validFromDate;
        }
        return null;
    }

    @Override
    public void setDateValidFrom(LocalDate date) {
        if (getBaseCalendar() != null) {
            getBaseCalendar().setValidFrom(date, selectedDate);
        }
    }

    @Override
    public List<CalendarData> getHistoryVersions() {
        if (getBaseCalendar() == null) {
            return null;
        }

        return getBaseCalendar().getCalendarDataVersions();
    }

    @Override
    public void createNewVersion(LocalDate startDate, LocalDate expiringDate,
            BaseCalendar baseCalendar) {
        if (getBaseCalendar() != null) {
            if (expiringDate != null) {
                expiringDate = expiringDate.plusDays(1);
            }
            getBaseCalendar().newVersion(startDate, expiringDate,
                    baseCalendar);
        }
    }

    @Override
    public boolean isLastVersion(LocalDate selectedDate) {
        if (getBaseCalendar() != null) {
            return getBaseCalendar().isLastVersion(selectedDate);
        }
        return false;
    }

    @Override
    public boolean isFirstVersion(LocalDate selectedDate) {
        if (getBaseCalendar() != null) {
            return getBaseCalendar().isFirstVersion(selectedDate);
        }
        return false;
    }

    @Override
    public void checkAndChangeStartDate(CalendarData version, Date date)
            throws ValidationException {

        if (date == null) {
            if (version.equals(getBaseCalendar().getFirstCalendarData())) {
                return;
            } else {
                throw new ValidationException(_("This date can not be empty"));
            }
        }

        LocalDate newStartDate = LocalDate.fromDateFields(date);
        CalendarData prevVersion = getBaseCalendar().getPrevious(version);
        if ((newStartDate != null) && (prevVersion != null)) {
            if (getBaseCalendar().getPrevious(prevVersion) == null) {
                return;
            }
            LocalDate prevStartDate = getBaseCalendar()
                    .getPrevious(prevVersion).getExpiringDate();
            if ((prevStartDate == null)
                    || ((newStartDate
                            .compareTo(prevStartDate) > 0))) {
                prevVersion.setExpiringDate(newStartDate);
                return;
            }
        }
        throw new ValidationException(
                _("This date can not include the whole previous work week"));
    }

    @Override
    public void checkChangeExpiringDate(CalendarData version, Date date) {
        Integer index = getBaseCalendar().getCalendarDataVersions().indexOf(
                version);

        if (date == null) {
            if (version.equals(getBaseCalendar().getLastCalendarData())) {
                return;
            } else {
                throw new ValidationException(_("This date can not be empty"));
            }
        }

        LocalDate newExpiringDate = LocalDate.fromDateFields(date);
        if ((index < getBaseCalendar().getCalendarDataVersions().size() - 1)) {
            LocalDate nextExpiringDate = getBaseCalendar()
                    .getCalendarDataVersions().get(index + 1).getExpiringDate();
            if ((nextExpiringDate == null)
                    || (newExpiringDate.compareTo(nextExpiringDate) < 0)) {
                return;
            }
        }
        throw new ValidationException(
                _("This date can not include the whole next work week"));
    }

    @Override
    public String getName() {
        if (getBaseCalendar() != null) {
            return getBaseCalendar().getName();
        }
        return null;
    }

    @Override
    public LocalDate getValidFrom(CalendarData calendarData) {
        if (getBaseCalendar() != null) {
            return getBaseCalendar().getValidFrom(calendarData);
        }

        return null;
    }

    /*
     * Final conversation steps
     */

    @Override
    @Transactional(rollbackFor = ValidationException.class)
    public void confirmSave() throws ValidationException {
        confirmSave(getBaseCalendar());
    }

    @Transactional(rollbackFor = ValidationException.class)
    private void confirmSave(BaseCalendar calendar) throws ValidationException {
        checkInvalidValuesCalendar(calendar);
        baseCalendarDAO.save(calendar);
    }

    @Override
    @Transactional(rollbackFor = ValidationException.class)
    public void confirmSaveAndContinue() throws ValidationException {
        BaseCalendar baseCalendar = getBaseCalendar();
        confirmSave(baseCalendar);
        dontPoseAsTransientObjectAnymore(baseCalendar);
    }

    /**
     * Don't pose as transient anymore calendar and all data hanging from
     * calendar (data versions, availabilities and exceptions)
     *
     * @param calendar
     */
    private void dontPoseAsTransientObjectAnymore(BaseCalendar calendar) {
        calendar.dontPoseAsTransientObjectAnymore();
        for (CalendarData each: calendar.getCalendarDataVersions()) {
            each.dontPoseAsTransientObjectAnymore();
        }
        for (CalendarAvailability each : calendar.getCalendarAvailabilities()) {
            each.dontPoseAsTransientObjectAnymore();
        }
        for (CalendarException each : calendar.getExceptions()) {
            each.dontPoseAsTransientObjectAnymore();
        }
    }

    @Override
    public void checkInvalidValuesCalendar(BaseCalendar entity)
            throws ValidationException {
        if (baseCalendarDAO.thereIsOtherWithSameName(entity)) {
            InvalidValue[] invalidValues2 = { new InvalidValue(_(
                    "{0} already exists", entity.getName()),
                    BaseCalendar.class, "name", entity.getName(), entity) };
            throw new ValidationException(invalidValues2,
                    _("Could not save new calendar"));
        }
    }

    @Transactional
    public void generateCalendarCodes() {
        if (getBaseCalendar().isCodeAutogenerated()) {
            baseCalendar
                    .generateCalendarExceptionCodes(getNumberOfDigitsCode());
        }
    }

    @Override
    @Transactional
    public void confirmRemove(BaseCalendar calendar) {
        try {
            baseCalendarDAO.remove(calendar.getId());
        } catch (InstanceNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void cancel() {
        resetState();
    }

    private void resetState() {
        baseCalendar = null;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isDefaultCalendar(BaseCalendar baseCalendar) {
        Configuration configuration = configurationDAO.getConfiguration();
        if (configuration == null) {
            return false;
        }
        BaseCalendar defaultCalendar = configuration.getDefaultCalendar();
        if (defaultCalendar == null) {
            return false;
        }
        if (baseCalendar == null) {
            return false;
        }
        return baseCalendar.getId().equals(
                defaultCalendar
                        .getId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<CalendarExceptionType> getCalendarExceptionTypes() {
        return calendarExceptionTypeDAO.list(CalendarExceptionType.class);
    }

    @Override
    public Set<CalendarException> getCalendarExceptions() {
        if (getBaseCalendar() == null) {
            return new HashSet<CalendarException>();
        }
        return getBaseCalendar().getExceptions();
    }

    @Override
    public boolean isOwnException(CalendarException exception) {
        if (getBaseCalendar() == null) {
            return false;
        }
        return getBaseCalendar().getOwnExceptions().contains(exception);
    }

    @Override
    public void removeException(LocalDate date) {
        if (getBaseCalendar() != null) {
            getBaseCalendar().removeExceptionDay(date);
        }
    }

    @Override
    public CalendarExceptionType getCalendarExceptionType() {
        if (getBaseCalendar() == null) {
            return null;
        }

        return getBaseCalendar().getExceptionType(selectedDate);
    }

    @Override
    public CalendarExceptionType getCalendarExceptionType(LocalDate date) {
        if (getBaseCalendar() == null) {
            return null;
        }

        return getBaseCalendar().getExceptionType(date);
    }

    @Override
    public void updateException(CalendarExceptionType type,
            LocalDate startDate, LocalDate endDate, Capacity capacity) {
        for (LocalDate date = new LocalDate(startDate); date
                .compareTo(new LocalDate(endDate)) <= 0; date = date
                .plusDays(1)) {
            if (getTypeOfDay(date).equals(DayType.OWN_EXCEPTION)) {
                if (type == null) {
                    getBaseCalendar().removeExceptionDay(date);
                } else {
                    getBaseCalendar().updateExceptionDay(date, capacity, type);
                }
            } else {
                if (type != null) {
                    CalendarException day = CalendarException.create(date,
                            capacity, type);
                    getBaseCalendar().addExceptionDay(day);
                }
            }
        }
    }

    @Override
    public void removeCalendarData(CalendarData calendarData) {
        if (getBaseCalendar() != null) {
            getBaseCalendar().removeCalendarData(calendarData);
        }
    }

    @Override
    public CalendarData getLastCalendarData() {
        if (getBaseCalendar() == null) {
            return null;
        }
        return getBaseCalendar().getLastCalendarData();
    }

    @Override
    public CalendarData getCalendarData() {
        if (getBaseCalendar() == null) {
            return null;
        }
        LocalDate selectedDay = getSelectedDay();
        if (selectedDay == null) {
            return null;
        }
        return getBaseCalendar().getCalendarData(selectedDay);
    }

    @Override
    public boolean isResourceCalendar() {
        if (getBaseCalendar() == null) {
            return false;
        }
        return getBaseCalendar() instanceof ResourceCalendar;
    }

    @Override
    public List<CalendarAvailability> getCalendarAvailabilities() {
        if (getBaseCalendar() == null) {
            return null;
        }
        return getBaseCalendar().getCalendarAvailabilities();
    }

    @Override
    public void removeCalendarAvailability(
            CalendarAvailability calendarAvailability) {
        if (getBaseCalendar() != null) {
            getBaseCalendar().removeCalendarAvailability(calendarAvailability);
        }
    }

    @Override
    public void createCalendarAvailability() {
        if (getBaseCalendar() != null) {
            LocalDate startDate = new LocalDate();
            CalendarAvailability lastCalendarAvailability = getBaseCalendar()
            .getLastCalendarAvailability();
            if (lastCalendarAvailability != null) {
                if (lastCalendarAvailability.getEndDate() == null) {
                    startDate = lastCalendarAvailability.getStartDate();
                } else {
                    startDate = lastCalendarAvailability.getEndDate();
                }
                startDate = startDate.plusDays(1);
            }

            CalendarAvailability calendarAvailability = CalendarAvailability
                    .create(startDate, null);
            calendarAvailability.setCode("");
            getBaseCalendar().addNewCalendarAvailability(calendarAvailability);
        }
    }

    @Override
    public void setStartDate(CalendarAvailability calendarAvailability,
            LocalDate startDate) throws IllegalArgumentException {
        if (getBaseCalendar() != null) {
            getBaseCalendar().setStartDate(calendarAvailability, startDate);
        }
    }

    @Override
    public void setEndDate(CalendarAvailability calendarAvailability,
            LocalDate endDate) throws IllegalArgumentException {
        if (getBaseCalendar() != null) {
            getBaseCalendar().setEndDate(calendarAvailability, endDate);
        }
    }

    @Override
    public EntityNameEnum getEntityName() {
        return EntityNameEnum.CALENDAR;
    }

    @Override
    public Set<IntegrationEntity> getChildren() {
        Set<IntegrationEntity> children = new HashSet<IntegrationEntity>();
        if (baseCalendar != null) {
            children.addAll(baseCalendar.getExceptions());
            children.addAll(baseCalendar.getCalendarDataVersions());
            children.addAll(baseCalendar.getCalendarAvailabilities());
        }
        return children;
    }

    @Override
    public IntegrationEntity getCurrentEntity() {
        return this.baseCalendar;
    }

    @Override
    public boolean isLastActivationPeriod(
            CalendarAvailability calendarAvailability) {
        if (getBaseCalendar() != null) {
            return getBaseCalendar().isLastCalendarAvailability(
                    calendarAvailability);
        }
        return false;
    }

    @Override
    @Transactional(readOnly = true)
    public void checkIsReferencedByOtherEntities(BaseCalendar calendar) throws ValidationException {
        baseCalendarDAO.checkIsReferencedByOtherEntities(calendar);
    }

}
