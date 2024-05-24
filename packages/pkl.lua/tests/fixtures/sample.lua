--[[
This is an example config file written in Lua.
It consists of key/value pairs and only literals, no expressions.
]]
greeting="Hello, world!"
snippet=[=[
Long Lua strings can be written inside brackets like this: [[
This is a multiline string.
It ends at the close double-bracket.]]
]=]
hex_floats={
    0x10.0,
    0x0.fffff,
    -0x32p3,
}
tableKeys={
    identifier = true;
    ["string"] = "yes";
    [42] = "the meaning of life";
    [3.14159] = "pi";
    [true] = "very true";
    [{1, 2, 3}] = "even tables can be keys";
    -- but nil and NaN cannot
}
