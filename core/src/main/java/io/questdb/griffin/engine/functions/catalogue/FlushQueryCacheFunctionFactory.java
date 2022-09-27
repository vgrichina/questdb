/*******************************************************************************
 *     ___                  _   ____  ____
 *    / _ \ _   _  ___  ___| |_|  _ \| __ )
 *   | | | | | | |/ _ \/ __| __| | | |  _ \
 *   | |_| | |_| |  __/\__ \ |_| |_| | |_) |
 *    \__\_\\__,_|\___||___/\__|____/|____/
 *
 *  Copyright (c) 2014-2019 Appsicle
 *  Copyright (c) 2019-2022 QuestDB
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 ******************************************************************************/

package io.questdb.griffin.engine.functions.catalogue;

import io.questdb.MessageBus;
import io.questdb.cairo.CairoConfiguration;
import io.questdb.cairo.sql.Function;
import io.questdb.cairo.sql.Record;
import io.questdb.griffin.FunctionFactory;
import io.questdb.griffin.SqlExecutionContext;
import io.questdb.griffin.engine.functions.BooleanFunction;
import io.questdb.log.Log;
import io.questdb.log.LogFactory;
import io.questdb.std.IntList;
import io.questdb.std.ObjList;
import io.questdb.std.Os;

public class FlushQueryCacheFunctionFactory implements FunctionFactory {

    private static final Log LOG = LogFactory.getLog("flush-query-cache");

    @Override
    public String getSignature() {
        return "flush_query_cache()";
    }

    @Override
    public Function newInstance(int position,
                                ObjList<Function> args,
                                IntList argPositions,
                                CairoConfiguration configuration,
                                SqlExecutionContext sqlExecutionContext
    ) {
        return new FlushQueryCacheFunction(sqlExecutionContext.getMessageBus());
    }

    private static class FlushQueryCacheFunction extends BooleanFunction {

        private final MessageBus messageBus;

        public FlushQueryCacheFunction(MessageBus messageBus) {
            this.messageBus = messageBus;
        }

        @Override
        public boolean getBool(Record rec) {
            LOG.info().$("flushing query caches").$();

            while (true) {
                final long pubCursor = messageBus.getQueryCacheEventPubSeq().next();
                if (pubCursor > -1) {
                    messageBus.getQueryCacheEventPubSeq().done(pubCursor);
                    return true;
                } else if (pubCursor == -1) {
                    // Queue is full
                    LOG.error().$("cannot publish flush query cache event to a full queue").$();
                    return false;
                }
                Os.pause();
            }
        }

        @Override
        public boolean isReadThreadSafe() {
            return true;
        }
    }
}
