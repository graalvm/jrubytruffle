module Bundler
  class CurrentRuby
    # TruffleRuby: added method
    def truffleruby?
      defined?(RUBY_ENGINE) && RUBY_ENGINE == "truffleruby"
    end
  end
end
