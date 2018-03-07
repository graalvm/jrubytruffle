# File is formatted with
# `jsonnet fmt --indent 2 --max-blank-lines 2 --sort-imports --string-style d --comment-style h -i utils.libsonnet`

# Helper functions for CI definition. They only help with the structure
# and warn about broken rules. With few small modifications in final build
# field for comprehension they could be completely dropped and this file would
# still produce the same result.
{
  # It computes full name for each part (e.g. $.platform.linux) and adds field
  # `included_parts+: ['$.platform.linux']` into each part. When parts are
  # added together to form a build the array in `included_parts` composes and
  # keeps track of all parts used to compose the build.
  # The function returns part_definitions with the included_parts added to
  # each part.
  add_inclusion_tracking:: function(part_definitions, prefix, contains_parts)
    {
      local content = part_definitions[k],
      local new_prefix = prefix + "." + k,

      [k]: (
        if std.type(content) == "object" && contains_parts
        then
          # process the part object
          content {
            included_parts+:
              [new_prefix],
          } + (
            if std.objectHasAll(content, "is_before")
            then { is_before:: [[new_prefix, b] for b in content.is_before] }
            else {}
          ) + (
            if std.objectHasAll(content, "is_after")
            then { is_after:: [[new_prefix, a] for a in content.is_after] }
            else {}
          )
        else
          # look recursively for parts
          if std.type(content) == "object"
          then $.add_inclusion_tracking(
            content,
            new_prefix,
            # assume parts are always nested 1 level in a group
            true
          )
          else content
      )
      for k in std.objectFields(part_definitions)
    },

  # checks order of parts in included_parts optionally
  # specified in is_after and is_before
  check_order:: function(build)
    local before_check =
      (if std.objectHasAll(build, "is_before")
       then
         std.foldl(
           function(r, specifee_before)
             local specifee = specifee_before[0],
                   before = specifee_before[1],
                   specifee_index = $.index(build.included_parts, specifee)[0],
                   before_index = $.index(build.included_parts, before)[0];
             (r && specifee_index < before_index) ||
             error specifee + " has to be before " + before +
                   "\nfound in " + build.name + "\nhaving " + build.included_parts,
           build.is_before,
           true
         )
       else true);
    local after_check =
      (if std.objectHasAll(build, "is_after")
       then
         std.foldl(
           function(r, specifee_after)
             local specifee = specifee_after[0],
                   after = specifee_after[1],
                   specifee_index = $.index(build.included_parts, specifee)[0],
                   after_index = $.index(build.included_parts, after)[0];
             (r && specifee_index > after_index) ||
             error specifee + " has to be after " + after +
                   "\nfound in " + build.name + "\nhaving " + build.included_parts,
           build.is_after,
           true
         )
       else true);
    if before_check && after_check then build,

  # Ensures that no part is included twice using `included_parts` field.
  included_once_check:: function(build)
    local name = if std.objectHas(build, "name") then build.name else build;
    # collect repeated parts
    local repeated = std.foldl(function(r, i)
                                 if std.count(build.included_parts, i) == 1
                                 then r else r + [i],
                               build.included_parts,
                               []);
    if std.length(repeated) == 0
    then build
    else error "Parts " + repeated +
               " are used more than once in build: " + name +
               ". See for duplicates: " + build.included_parts,

  # should build be used with given restrictions
  used:: function(restriction, build)
    if std.length(restriction) == 0
    then true
    else std.count(restriction, build.name) > 0,

  # perform all checks
  check_builds:: function(restrict_to, builds)
    [
      $.included_once_check($.check_order(build)) {
        environment+: {
          # Add PARTS_INCLUDED_IN_BUILD env var so the parts used
          # in the build are printed in its log
          PARTS_INCLUDED_IN_BUILD:
            std.join(
              ", ",
              std.map(
                # there is no way to escape $ afaik, stripping it instead,
                # otherwise it is interpreted as variable
                function(name) std.substr(name, 2, std.length(name) - 2),
                build.included_parts
              )
            ),
        },
      }
      for build in builds
      if $.used(restrict_to, build)
    ],

  # Try to read a field, if not present print error including the name
  # of the object
  debug_read:: function(obj, field)
    if std.objectHasAll(obj, field)
    then obj[field]
    else error "missing field: " + field + " in " + obj.name,

  index:: function(arr, obj)
    std.filter(function(i) obj == arr[i],
               std.range(0, std.length(arr) - 1)),
}
