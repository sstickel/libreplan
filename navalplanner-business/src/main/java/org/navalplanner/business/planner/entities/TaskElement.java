/*
 * This file is part of ###PROJECT_NAME###
 *
 * Copyright (C) 2009 Fundación para o Fomento da Calidade Industrial e
 *                    Desenvolvemento Tecnolóxico de Galicia
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

package org.navalplanner.business.planner.entities;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import org.apache.commons.lang.Validate;
import org.joda.time.LocalDate;
import org.navalplanner.business.calendars.entities.BaseCalendar;
import org.navalplanner.business.common.BaseEntity;
import org.navalplanner.business.orders.entities.OrderElement;
import org.navalplanner.business.planner.entities.Dependency.Type;

/**
 * @author Óscar González Fernández <ogonzalez@igalia.com>
 */
public abstract class TaskElement extends BaseEntity {

    private Date startDate;

    private Date endDate;

    private String name;

    private String notes;

    private TaskGroup parent;

    protected Integer shareOfHours;

    private OrderElement orderElement;

    private Set<Dependency> dependenciesWithThisOrigin = new HashSet<Dependency>();

    private Set<Dependency> dependenciesWithThisDestination = new HashSet<Dependency>();

    private BaseCalendar calendar;

    public Integer getWorkHours() {
        if (shareOfHours != null) {
            return shareOfHours;
        }
        return defaultWorkHours();
    }

    protected abstract Integer defaultWorkHours();

    protected void copyPropertiesFrom(TaskElement task) {
        this.name = task.getName();
        this.notes = task.getNotes();
        this.startDate = task.getStartDate();
        this.orderElement = task.getOrderElement();
    }

    protected void copyDependenciesTo(TaskElement result) {
        for (Dependency dependency : getDependenciesWithThisOrigin()) {
            Dependency.create(result, dependency.getDestination(),
                    dependency.getType());
        }
        for (Dependency dependency : getDependenciesWithThisDestination()) {
            Dependency.create(dependency.getOrigin(), result,
                    dependency.getType());
        }
    }

    protected void copyParenTo(TaskElement result) {
        if (this.getParent() != null) {
            this.getParent().addTaskElement(result);
        }
    }

    public TaskGroup getParent() {
        return parent;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public void setOrderElement(OrderElement orderElement)
            throws IllegalArgumentException, IllegalStateException {
        Validate.notNull(orderElement, "orderElement must be not null");
        if (this.orderElement != null) {
            throw new IllegalStateException(
                    "once a orderElement is set, it cannot be changed");
        }
        this.orderElement = orderElement;
    }

    public OrderElement getOrderElement() {
        return orderElement;
    }

    public Set<Dependency> getDependenciesWithThisOrigin() {
        return Collections.unmodifiableSet(dependenciesWithThisOrigin);
    }

    public Set<Dependency> getDependenciesWithThisDestination() {
        return Collections.unmodifiableSet(dependenciesWithThisDestination);
    }

    public Date getStartDate() {
        return startDate;
    }

    public void setStartDate(Date startDate) {
        this.startDate = startDate;

    }

    /**
     * Sets the startDate to newStartDate. It can update the endDate
     * @param newStartDate
     */
    public void moveTo(Date newStartDate) {
        long durationMilliseconds = this.endDate.getTime()
                - this.startDate.getTime();
        this.startDate = newStartDate;
        this.endDate = new Date(this.startDate.getTime() + durationMilliseconds);
        moveAllocations();
    }

    protected abstract void moveAllocations();

    public Date getEndDate() {
        return endDate;
    }

    public void setEndDate(Date endDate) {
        this.endDate = endDate;
    }

    void add(Dependency dependency) {
        if (this.equals(dependency.getOrigin())) {
            dependenciesWithThisOrigin.add(dependency);
        }
        if (this.equals(dependency.getDestination())) {
            dependenciesWithThisDestination.add(dependency);
        }
    }

    private void removeDependenciesWithThisOrigin(TaskElement origin, Type type) {
        ArrayList<Dependency> toBeRemoved = new ArrayList<Dependency>();
        for (Dependency dependency : dependenciesWithThisDestination) {
            if (dependency.getOrigin().equals(origin)
                    && dependency.getType().equals(type)) {
                toBeRemoved.add(dependency);
            }
        }
        dependenciesWithThisDestination.removeAll(toBeRemoved);
    }

    public void removeDependencyWithDestination(TaskElement destination, Type type) {
        ArrayList<Dependency> toBeRemoved = new ArrayList<Dependency>();
        for (Dependency dependency : dependenciesWithThisOrigin) {
            if (dependency.getDestination().equals(destination)
                    && dependency.getType().equals(type)) {
                toBeRemoved.add(dependency);
            }
        }
        destination.removeDependenciesWithThisOrigin(this, type);
        dependenciesWithThisOrigin.removeAll(toBeRemoved);
    }

    public abstract boolean isLeaf();

    public abstract List<TaskElement> getChildren();

    protected void setParent(TaskGroup taskGroup) {
        this.parent = taskGroup;
    }

    public void detach() {
        detachDependencies();
        detachFromParent();
    }

    private void detachFromParent() {
        if (parent != null) {
            parent.remove(this);
        }
    }

    private void removeDependenciesWithOrigin(TaskElement t) {
        List<Dependency> dependenciesToRemove = getDependenciesWithOrigin(t);
        dependenciesWithThisDestination.removeAll(dependenciesToRemove);
    }

    private void removeDependenciesWithDestination(TaskElement t) {
        List<Dependency> dependenciesToRemove = getDependenciesWithDestination(t);
        dependenciesWithThisOrigin.removeAll(dependenciesToRemove);
    }

    private List<Dependency> getDependenciesWithDestination(TaskElement t) {
        ArrayList<Dependency> result = new ArrayList<Dependency>();
        for (Dependency dependency : dependenciesWithThisOrigin) {
            if (dependency.getDestination().equals(t)) {
                result.add(dependency);
            }
        }
        return result;
    }

    private List<Dependency> getDependenciesWithOrigin(TaskElement t) {
        ArrayList<Dependency> result = new ArrayList<Dependency>();
        for (Dependency dependency : dependenciesWithThisDestination) {
            if (dependency.getOrigin().equals(t)) {
                result.add(dependency);
            }
        }
        return result;
    }

    private void detachDependencies() {
        detachOutcomingDependencies();
        detachIncomingDependencies();
    }

    private void detachIncomingDependencies() {
        Set<TaskElement> tasksToNotify = new HashSet<TaskElement>();
        for (Dependency dependency : dependenciesWithThisDestination) {
            tasksToNotify.add(dependency.getOrigin());
        }
        for (TaskElement taskElement : tasksToNotify) {
            taskElement.removeDependenciesWithDestination(this);
        }
    }

    private void detachOutcomingDependencies() {
        Set<TaskElement> tasksToNotify = new HashSet<TaskElement>();
        for (Dependency dependency : dependenciesWithThisOrigin) {
            tasksToNotify.add(dependency.getDestination());
        }
        for (TaskElement taskElement : tasksToNotify) {
            taskElement.removeDependenciesWithOrigin(this);
        }
    }

    public void setCalendar(BaseCalendar calendar) {
        this.calendar = calendar;
    }

    public BaseCalendar getCalendar() {
        return calendar;
    }

    public abstract Set<ResourceAllocation<?>> getResourceAllocations();

    public SortedMap<LocalDate, Integer> getHoursAssignedByDay() {
        SortedMap<LocalDate, Integer> result = new TreeMap<LocalDate, Integer>();
        for (ResourceAllocation<?> resourceAllocation : getResourceAllocations()) {
            for (DayAssignment each : resourceAllocation
                    .getAssignments()) {
                addToResult(result, each.getDay(), each.getHours());
            }
        }
        return result;
    }

    private void addToResult(SortedMap<LocalDate, Integer> result,
            LocalDate date, int hours) {
        int current = result.get(date) != null ? result.get(date) : 0;
        result.put(date, current + hours);
    }

}
