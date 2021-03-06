/*
 * This file is part of LibrePlan
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

package org.libreplan.business.calendars.entities;

import javax.validation.constraints.AssertTrue;
import javax.validation.constraints.NotNull;
import org.joda.time.LocalDate;
import org.libreplan.business.resources.entities.Resource;

/**
 * Calendar for a {@link Resource}.
 *
 * @author Manuel Rego Casasnovas <mrego@igalia.com>
 * @author Lorenzo Tilve Álvaro <ltilve@igalia.com>
 */
public class ResourceCalendar extends BaseCalendar {

    private Resource resource;

    private Integer capacity = 1;

    public Integer getCapacity() {
        if (capacity == null) {
            return 1;
        }

        return capacity;
    }

    public void setCapacity(Integer capacity) {
        this.capacity = capacity;
    }

    public static ResourceCalendar create() {
        return create(new ResourceCalendar(CalendarData.create()));
    }

    /**
     * Constructor for hibernate. Do not use!
     */
    public ResourceCalendar() {
    }

    private ResourceCalendar(CalendarData calendarData) {
        super(calendarData);
        CalendarAvailability calendarAvailability = CalendarAvailability.create(new LocalDate(), null);
        addNewCalendarAvailability(calendarAvailability);
    }

    @Override
    protected Capacity multiplyByCalendarUnits(Capacity capacity) {
        return capacity.multiplyBy(getCapacity());
    }

    @AssertTrue(message = "Capacity must be a positive integer number")
    public boolean isCapacityPositiveIntegerNumberConstraint() {
        return (capacity >= 1);
    }

    @NotNull(message = "resource not specified")
    public Resource getResource() {
        return resource;
    }

    public void setResource(Resource resource) {
        this.resource = resource;
    }

    @Override
    public Boolean isCodeAutogenerated() {
        return true;
    }

}
