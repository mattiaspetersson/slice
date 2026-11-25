Slc {
	*new {|key ...args|
		^Pdef(key, Pbind(*args));
	}
}