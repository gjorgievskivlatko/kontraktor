package de.ruedigermoeller.abstraktor;

import de.ruedigermoeller.abstraktor.impl.ActorProxyFactory;
import de.ruedigermoeller.abstraktor.impl.Dispatcher;

/**
 * Copyright (c) 2012, Ruediger Moeller. All rights reserved.
 * <p/>
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 * <p/>
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * <p/>
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 * <p/>
 * Date: 04.01.14
 * Time: 19:50
 * To change this template use File | Settings | File Templates.
 */
public class Actors {

    static Actors instance = new Actors();

    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // static API

    /**
     * create an new actor. If this is called outside an actor, a new Dispatcher(-Thread) will be scheduled. If
     * called from inside actor code, the new actor will share the thread+queue with the caller.
     *
     * @param actorClazz
     * @param <T>
     * @return
     */
    public static <T extends Actor> T AsActor(Class<? extends Actor> actorClazz) {
        return (T) instance.newProxy(actorClazz);
    }

    /**
     * create an new actor dispatched in the given Dispatcher (-Thread)
     *
     * @param actorClazz
     * @param <T>
     * @return
     */
    public static <T extends Actor> T AsActor(Class<? extends Actor> actorClazz, Dispatcher disp) {
        return (T) instance.newProxy(actorClazz,disp);
    }

    /**
     * create a new actor with a newly created Dispatcher (-Thread)
     * @param actorClazz
     * @param <T>
     * @return
     */
    public static <T extends Actor> T SpawnActor(Class<? extends Actor> actorClazz) {
        return (T) instance.newProxy(actorClazz, instance.newDispatcher() );
    }

    // end static API
    //
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////


    protected Actors() {
        factory = new ActorProxyFactory();
    }

    protected ActorProxyFactory factory;

    protected ActorProxyFactory getFactory() {
        return factory;
    }

    protected Actor newProxy(Class<? extends Actor> clz, Dispatcher disp ) {
        try {
            Actor res = clz.newInstance();
            res.dispatcher = disp;
            Actor proxy = getFactory().instantiateProxy(res);
            return proxy;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    protected Actor newProxy(Class<? extends Actor> clz) {
        if ( Dispatcher.getThreadDispatcher() != null ) {
            return newProxy( clz, Dispatcher.getThreadDispatcher() );
        } else {
            try {
                return newProxy(clz, newDispatcher());
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }
    }

    /**
     * return a new dispatcher backed by a new thread. Overriding classes should *not*
     * return existing dispatchers here, as this can be used to isolate blocking code from the actor flow.
     *
     * @return
     * @throws InstantiationException
     * @throws IllegalAccessException
     */
    protected Dispatcher newDispatcher() {
        return new Dispatcher();
    }

}
