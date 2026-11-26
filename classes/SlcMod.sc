/*
Adapter class for SLiCE, for plugging in Ndef's into non-control inputs of patterns.
I.e., for use with MIDI instruments and lang-side lfo:s.
*/

SlcMod {
	var <key, func, rate;
	var server, <ndef, <ndefBus, reader, responder, <replyID, <oscKey;
	var <>consumers;
	classvar <all;

	*initClass {
		all = IdentityDictionary.new;
	}

	*new {|key, func, rate = 30|
		var existing;
		key = key.asSymbol;
		existing = all[key];
		if(existing.isNil) {
			^super.newCopyArgs(key, func, rate).init;
		}{
			^existing.updateNdef(key, func);
		};
	}

	init {
		all[key] = this;
		consumers = List.new;
		server = SLiCE.instance.server ? Server.default;
		replyID = UniqueID.next;
		oscKey = ("NdefMod_" ++ replyID).asSymbol;

		ndef = Ndef(key, func);
		ndefBus = ndef.bus;

		fork{
			server.sync;

			reader = Synth.after(ndef.group, \slcModReader, [
				\bus, ndefBus,
				\replyID, replyID,
				\rate, rate
			]);
		};
	}

	updateNdef {|k, f|
		key = k;
		func = f;
		ndef = Ndef(key, func);
		ndefBus = ndef.bus;
	}

	free {
		reader.free;
		OSCdef(oscKey).free;
		Ndef(key).free;
		all[key] = nil;
	}

	*remove {|consumer|
		// remove this SlcMod from a consuming pattern key
		SlcMod.all.keysValuesDo{|slcModKey, slcMod|
			if(slcMod.notNil and: {slcMod.consumers.includes(consumer)}) {
				slcMod.free;
			};
		};
	}

	*audioMod {|envir, key, defaultValue = 0|
		switch(envir[key].class,
			Nil, {
				SlcMod.remove(key);
				^defaultValue
			},

			SlcMod, {
				envir[key].consumers.add(key);
				^envir[key].ndef.asMap;
			},

			{
				SlcMod.remove(key);
				^envir[key];
			}
		);
	}
}